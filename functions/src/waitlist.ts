import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import type { Caller } from "./common/caller";
import { AppError, notAllowed, notFound } from "./common/errors";
import type { Input } from "./common/validation";
import { col, loadSalon, loadServices, totals } from "./common/repo";
import { lastBookableDate } from "./domain/availability";
import { dateKeyOf } from "./domain/time";
import { computeAvailability } from "./booking/availabilityService";
import { notifyUser } from "./notifications/notify";

const MAX_ACTIVE_ENTRIES = 10;
/** Isti korisnik se ne obaveštava češće od jednom u 15 minuta za isti dan. */
const RENOTIFY_MS = 15 * 60_000;

export async function joinWaitlist(caller: Caller, input: Input<"joinWaitlist">, nowMs = Date.now()): Promise<{ waitlistId: string }> {
  const salon = await loadSalon(input.salonId);
  if (!salon.published) throw notFound("Salon");
  const services = await loadServices(input.salonId, input.serviceIds);
  const today = dateKeyOf(nowMs, salon.timezone);
  if (input.dateKey < today || input.dateKey > lastBookableDate(nowMs, salon.rules, salon.timezone)) {
    throw new AppError("POLICY_VIOLATION", "Date is outside the booking window", { reason: "OUTSIDE_WINDOW" });
  }

  const existing = await col.waitlist().where("uid", "==", caller.uid).where("dateKey", ">=", today).get();
  const active = existing.docs.filter((d) => ["waiting", "notified"].includes(d.data().status as string));
  const same = active.find((d) => d.data().salonId === input.salonId && d.data().dateKey === input.dateKey);
  if (same) {
    await same.ref.update({
      serviceIds: input.serviceIds,
      stylistId: input.stylistId,
      durationMin: totals(services).durationMin,
      status: "waiting",
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { waitlistId: same.id };
  }
  if (active.length >= MAX_ACTIVE_ENTRIES) {
    throw new AppError("POLICY_VIOLATION", "Too many waitlist entries", { reason: "MAX_WAITLIST" });
  }
  const ref = col.waitlist().doc();
  await ref.set({
    uid: caller.uid,
    salonId: salon.id,
    salonName: salon.name,
    dateKey: input.dateKey,
    serviceIds: input.serviceIds,
    serviceNames: services.map((s) => s.name),
    stylistId: input.stylistId,
    durationMin: totals(services).durationMin,
    status: "waiting",
    notifiedAt: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  return { waitlistId: ref.id };
}

export async function leaveWaitlist(caller: Caller, input: Input<"leaveWaitlist">): Promise<void> {
  const ref = col.waitlist().doc(input.waitlistId);
  const snap = await ref.get();
  if (!snap.exists) return;
  if (snap.data()?.uid !== caller.uid) throw notAllowed();
  await ref.delete();
}

/**
 * Kad se termin oslobodi: svi sa liste čekanja za taj salon i dan, kojima sada nešto odgovara,
 * dobijaju obaveštenje. Termin dobija onaj ko prvi potvrdi (hold je atomski).
 */
export async function notifyWaitlist(salonId: string, dateKey: string, excludeUid: string | null, nowMs = Date.now()): Promise<number> {
  const snaps = await col
    .waitlist()
    .where("salonId", "==", salonId)
    .where("dateKey", "==", dateKey)
    .where("status", "in", ["waiting", "notified"])
    .get();
  let notified = 0;
  for (const doc of snaps.docs) {
    const entry = doc.data();
    const uid = entry.uid as string;
    if (uid === excludeUid) continue;
    const lastNotified = entry.notifiedAt instanceof Timestamp ? entry.notifiedAt.toMillis() : 0;
    if (nowMs - lastNotified < RENOTIFY_MS) continue;
    try {
      const availability = await computeAvailability(
        {
          salonId,
          serviceIds: entry.serviceIds as string[],
          stylistId: (entry.stylistId as string | null) ?? null,
          fromDate: dateKey,
          days: 1,
        },
        nowMs,
      );
      if ((availability.days[0]?.slots.length ?? 0) === 0) continue;
      await doc.ref.update({ status: "notified", notifiedAt: Timestamp.fromMillis(nowMs) });
      await notifyUser(
        uid,
        "waitlist_slot",
        { salonName: entry.salonName as string, dateKey },
        {
          salonId,
          dateKey,
          serviceIds: entry.serviceIds as string[],
          stylistId: (entry.stylistId as string | null) ?? null,
        },
      );
      notified++;
    } catch (error) {
      logger.warn("Waitlist notify failed", { entry: doc.id, error: String(error) });
    }
  }
  return notified;
}

/** Posle potvrde termina, korisnikov unos na listi čekanja za taj salon i dan je ispunjen. */
export async function markWaitlistBooked(uid: string, salonId: string, dateKey: string): Promise<void> {
  const snaps = await col.waitlist().where("uid", "==", uid).where("dateKey", "==", dateKey).get();
  await Promise.all(
    snaps.docs
      .filter((d) => d.data().salonId === salonId && d.data().status !== "booked")
      .map((d) => d.ref.update({ status: "booked", updatedAt: FieldValue.serverTimestamp() })),
  );
}
