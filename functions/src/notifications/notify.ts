import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { isEmulator } from "../common/config";
import { messaging } from "../common/firebase";
import { col } from "../common/repo";
import type { Language, NotificationType, TemplateParams } from "./templates";
import { channelFor, render } from "./templates";

export interface NotificationData {
  appointmentId?: string;
  salonId?: string;
  dateKey?: string;
  serviceIds?: string[];
  stylistId?: string | null;
  reviewId?: string;
}

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

/** Pretvara podatke u string mapu (FCM data payload prima samo stringove). */
function toFcmData(type: NotificationType, inboxId: string, data: NotificationData): Record<string, string> {
  const result: Record<string, string> = { type, inboxId };
  for (const [key, value] of Object.entries(data)) {
    if (value === undefined || value === null) continue;
    result[key] = Array.isArray(value) ? value.join(",") : String(value);
  }
  return result;
}

/**
 * Šalje obaveštenje korisniku: uvek upisuje poruku u sanduče (users/{uid}/inbox), a push
 * šalje ako korisnik ima FCM tokene i nije isključio obaveštenja. Greške se beleže, ne bacaju.
 */
export async function notifyUser(
  uid: string,
  type: NotificationType,
  params: TemplateParams,
  data: NotificationData = {},
): Promise<void> {
  try {
    const userSnap = await col.users().doc(uid).get();
    const user = userSnap.data() ?? {};
    const language: Language = user.language === "en" ? "en" : "sr";
    const { title, body } = render(type, language, params);
    const inboxRef = col.inbox(uid).doc();
    await inboxRef.set({
      type,
      title,
      body,
      data: Object.fromEntries(Object.entries(data).filter(([, v]) => v !== undefined)),
      read: false,
      createdAt: FieldValue.serverTimestamp(),
    });

    const tokens = Array.isArray(user.fcmTokens) ? (user.fcmTokens as string[]).filter((t) => typeof t === "string") : [];
    if (isEmulator || tokens.length === 0 || user.notificationsEnabled === false) return;

    const response = await messaging.sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: toFcmData(type, inboxRef.id, data),
      android: {
        priority: "high",
        notification: {
          channelId: channelFor(type),
          tag: data.appointmentId ? `${type}:${data.appointmentId}` : inboxRef.id,
        },
      },
    });
    const invalid = response.responses
      .map((r, i) => (r.error && INVALID_TOKEN_CODES.has(r.error.code) ? tokens[i] : null))
      .filter((t): t is string => t !== null);
    if (invalid.length > 0) {
      await col.users().doc(uid).update({ fcmTokens: FieldValue.arrayRemove(...invalid) });
    }
  } catch (error) {
    logger.error("notifyUser failed", { uid, type, error: String(error) });
  }
}

/** Obaveštava vlasnika salona i (ako ima nalog) frizera kog se termin tiče. */
export async function notifyStaff(
  salon: { id: string; name: string; ownerUid: string | null; timezone: string },
  stylistUid: string | null,
  type: Extract<NotificationType, "staff_new_booking" | "staff_canceled" | "staff_rescheduled">,
  params: Omit<TemplateParams, "salonName" | "zone">,
  data: NotificationData,
  actorUid: string | null = null,
): Promise<void> {
  const recipients = new Set<string>();
  if (salon.ownerUid) recipients.add(salon.ownerUid);
  if (stylistUid) recipients.add(stylistUid);
  if (actorUid) recipients.delete(actorUid);
  await Promise.all(
    [...recipients].map((uid) =>
      notifyUser(uid, type, { ...params, salonName: salon.name, zone: salon.timezone }, data),
    ),
  );
}
