import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { db } from "./common/firebase";
import { col, loadSalon, loadStylist, toAppointment } from "./common/repo";
import type { AppointmentDoc } from "./common/repo";
import { dayRef, readDaysTx, withoutInterval, writeDay } from "./booking/days";
import { notifyUser } from "./notifications/notify";
import { refreshStylistBusy } from "./integrations/busy";
import { listConnections, loadConnection, updateSubscription } from "./integrations/store";
import { isProviderReady, providerFor } from "./integrations/registry";
import { webhookUrl } from "./integrations/oauth";
import { refreshNextFree } from "./salonDerived";

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

/** Briše istekle hold-ove. Dostupnost ih ignoriše odmah po isteku; ovo je samo čišćenje. */
export async function cleanupExpiredHolds(nowMs = Date.now()): Promise<number> {
  const snap = await col
    .appointments()
    .where("status", "==", "held")
    .where("holdExpiresAt", "<", Timestamp.fromMillis(nowMs))
    .limit(200)
    .get();
  let removed = 0;
  for (const doc of snap.docs) {
    await db.runTransaction(async (tx) => {
      const fresh = await tx.get(doc.ref);
      if (!fresh.exists) return;
      const appointment = toAppointment(fresh);
      if (appointment.status !== "held" || (appointment.holdExpiresAt ?? 0) >= nowMs) return;
      const r = dayRef(appointment.salonId, appointment.stylistId, appointment.dateKey);
      const days = await readDaysTx(tx, [r]);
      writeDay(tx, r, withoutInterval(days.get(r.path) ?? [], appointment.id, nowMs));
      tx.delete(doc.ref);
      removed++;
    });
  }
  return removed;
}

async function remind(appointment: AppointmentDoc, kind: "h24" | "h2" | "review"): Promise<void> {
  const ref = col.appointments().doc(appointment.id);
  // Oznaka se postavlja u transakciji pre slanja, da dve instance ne pošalju dvaput.
  const claimed = await db.runTransaction(async (tx) => {
    const fresh = toAppointment(await tx.get(ref));
    if (fresh.remindersSent[kind]) return false;
    tx.update(ref, { [`remindersSent.${kind}`]: true });
    return true;
  });
  if (!claimed || !appointment.customerUid) return;
  const salon = await loadSalon(appointment.salonId);
  const type = kind === "h24" ? "reminder_24h" : kind === "h2" ? "reminder_2h" : "review_request";
  await notifyUser(
    appointment.customerUid,
    type,
    { salonName: salon.name, start: appointment.start, zone: salon.timezone },
    { appointmentId: appointment.id, salonId: salon.id },
  );
}

/** Podsetnici 24 h i 2 h pre termina i molba za recenziju 1 h posle završenog termina. */
export async function sendReminders(nowMs = Date.now()): Promise<{ h24: number; h2: number; review: number }> {
  const counts = { h24: 0, h2: 0, review: 0 };
  const upcoming = await col
    .appointments()
    .where("status", "==", "confirmed")
    .where("start", ">", Timestamp.fromMillis(nowMs))
    .where("start", "<=", Timestamp.fromMillis(nowMs + 24 * HOUR))
    .get();
  for (const doc of upcoming.docs) {
    const a = toAppointment(doc);
    if (!a.customerUid) continue;
    const until = a.start - nowMs;
    if (until <= 2 * HOUR && !a.remindersSent.h2) {
      await remind(a, "h2");
      counts.h2++;
    } else if (until > 2 * HOUR && !a.remindersSent.h24) {
      await remind(a, "h24");
      counts.h24++;
    }
  }

  const finished = await col
    .appointments()
    .where("status", "==", "completed")
    .where("end", "<=", Timestamp.fromMillis(nowMs - HOUR))
    .where("end", ">=", Timestamp.fromMillis(nowMs - 7 * 24 * HOUR))
    .get();
  for (const doc of finished.docs) {
    const a = toAppointment(doc);
    if (!a.customerUid || a.reviewed || a.remindersSent.review) continue;
    await remind(a, "review");
    counts.review++;
  }
  return counts;
}

/**
 * Potvrđeni termin koji niko nije označio 12 h posle kraja smatra se završenim, da bi klijent
 * mogao da ostavi recenziju. Osoblje i dalje može da ga prebaci u „nije došao“.
 */
export async function autoCompleteAppointments(nowMs = Date.now()): Promise<number> {
  const snap = await col
    .appointments()
    .where("status", "==", "confirmed")
    .where("end", "<=", Timestamp.fromMillis(nowMs - 12 * HOUR))
    .limit(300)
    .get();
  const batch = db.batch();
  snap.docs.forEach((d) =>
    batch.update(d.ref, { status: "completed", autoCompleted: true, updatedAt: FieldValue.serverTimestamp() }),
  );
  if (!snap.empty) await batch.commit();
  return snap.size;
}

/** Osvežava keš zauzetosti spoljnih kalendara i obnavlja Google kanale pre isteka. */
export async function refreshExternalCalendars(nowMs = Date.now()): Promise<number> {
  const connections = await listConnections();
  const seen = new Set<string>();
  let refreshed = 0;
  for (const c of connections) {
    if (!isProviderReady(c.provider)) continue;
    try {
      const key = `${c.salonId}_${c.stylistId}`;
      const salon = await loadSalon(c.salonId);
      const stylist = await loadStylist(c.salonId, c.stylistId);
      if (!seen.has(key)) {
        seen.add(key);
        await refreshStylistBusy(c.salonId, stylist, salon.timezone, nowMs);
        refreshed++;
      }
      if (c.provider === "google" && nowMs - c.subscribedAt > 6 * 24 * HOUR) {
        const connection = await loadConnection(c.salonId, c.provider, c.stylistId);
        if (connection) {
          const id = await providerFor("google").subscribe(connection, webhookUrl("google"));
          if (id) await updateSubscription(c.salonId, c.stylistId, "google", id);
        }
      }
    } catch (error) {
      logger.warn("Periodic calendar refresh failed", { ...c, error: String(error) });
    }
  }
  return refreshed;
}

export async function refreshAllNextFree(nowMs = Date.now()): Promise<number> {
  const snap = await col.salons().where("published", "==", true).get();
  for (const doc of snap.docs) {
    await refreshNextFree(doc.id, nowMs).catch((error: unknown) =>
      logger.warn("nextFree refresh failed", { salonId: doc.id, error: String(error) }),
    );
  }
  return snap.size;
}

/** Brisanje starih evidencija webhookova i OAuth stanja. */
export async function cleanupServerRecords(nowMs = Date.now()): Promise<void> {
  const oldEvents = await col.webhookEvents().where("receivedAt", "<", Timestamp.fromMillis(nowMs - 30 * 24 * HOUR)).limit(400).get();
  const states = await col.oauthStates().where("expiresAt", "<", Timestamp.fromMillis(nowMs)).limit(400).get();
  const batch = db.batch();
  [...oldEvents.docs, ...states.docs].forEach((d) => batch.delete(d.ref));
  if (!oldEvents.empty || !states.empty) await batch.commit();
}
