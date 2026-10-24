import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db, auth } from "../common/firebase";
import { AppError } from "../common/errors";
import type { Caller } from "../common/caller";
import { assertCanManageStylist } from "../common/caller";
import type { Input } from "../common/validation";
import type { AppointmentDoc } from "../common/repo";
import { bookedServices, col, loadSalon, loadServices, loadStylist, totals } from "../common/repo";
import { fitsDay, isBusyActive } from "../domain/availability";
import { dateKeyOf, dateKeysInRange } from "../domain/time";
import type { BusyInterval } from "../domain/types";
import { externalBusy } from "../integrations/busy";
import { buildStylistDay } from "./availabilityService";
import { dayRef, readDaysTx, withInterval, withoutInterval, writeDay } from "./days";
import { afterBookingChange } from "./effects";

const HOUR = 3_600_000;

async function uidForPhone(phone: string | null): Promise<string | null> {
  if (!phone) return null;
  try {
    return (await auth.getUserByPhoneNumber(phone)).uid;
  } catch {
    return null;
  }
}

/**
 * Ručni unos termina (zakazan telefonom ili klijent bez najave). Proverava preklapanje sa
 * ostalim terminima; za klijenta bez najave dozvoljava i vreme van radnog vremena.
 * Ako broj telefona pripada korisniku aplikacije, termin se vidi i u njegovim „Moji termini“.
 */
export async function createManualBooking(
  caller: Caller,
  input: Input<"createManualBooking">,
  nowMs = Date.now(),
): Promise<AppointmentDoc> {
  assertCanManageStylist(caller, input.salonId, input.stylistId);
  const salon = await loadSalon(input.salonId);
  const [services, stylist] = await Promise.all([
    loadServices(input.salonId, input.serviceIds),
    loadStylist(input.salonId, input.stylistId),
  ]);
  if (!stylist.active) throw new AppError("VALIDATION", "Stylist is not active");
  if (input.start < nowMs - 12 * HOUR) {
    throw new AppError("POLICY_VIOLATION", "Start is too far in the past", { reason: "OUTSIDE_WINDOW" });
  }
  const { durationMin, totalPrice } = totals(services);
  const zone = salon.timezone;
  const dateKey = dateKeyOf(input.start, zone);
  const end = input.start + durationMin * 60_000;
  const external = await externalBusy(salon.id, [stylist], zone, nowMs);
  const customerUid = await uidForPhone(input.customerPhone);
  const ref = col.appointments().doc();

  const appointment = await db.runTransaction(async (tx) => {
    const ref2 = dayRef(salon.id, stylist.id, dateKey);
    const days = await readDaysTx(tx, [ref2]);
    const busy = days.get(ref2.path) ?? [];
    const day = buildStylistDay(salon, stylist, dateKey, busy, external.get(stylist.id) ?? []);
    // Za ručni unos najkraće vreme do termina ne važi, ali preklapanje važi uvek.
    const params = { dateKey, zone, durationMin, rules: { ...salon.rules, minNoticeMinutes: 0 }, nowMs };
    if (!fitsDay(input.start, day, params, { ignoreWorkingHours: input.walkIn })) {
      throw new AppError("SLOT_TAKEN", "Stylist is busy at that time", { date: dateKey, slots: [] });
    }
    writeDay(tx, ref2, withInterval(busy, { id: ref.id, start: input.start, end, kind: "appt" }, nowMs));
    const doc = {
      salonId: salon.id,
      salonName: salon.name,
      salonPhone: salon.phone,
      salonAddress: `${salon.address}, ${salon.city}`,
      stylistId: stylist.id,
      stylistName: stylist.name,
      customerUid,
      customerName: input.customerName,
      customerPhone: input.customerPhone,
      services: bookedServices(services),
      start: Timestamp.fromMillis(input.start),
      end: Timestamp.fromMillis(end),
      durationMin,
      totalPrice,
      status: "confirmed",
      holdExpiresAt: null,
      note: input.note,
      source: "manual",
      externalIds: {},
      anyStylist: false,
      dateKey,
      remindersSent: { h24: false, h2: false, review: false },
      reviewed: false,
      createdBy: caller.uid,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };
    tx.create(ref, doc);
    const result: AppointmentDoc = {
      id: ref.id,
      salonId: salon.id,
      salonName: salon.name,
      stylistId: stylist.id,
      stylistName: stylist.name,
      customerUid,
      customerName: input.customerName,
      customerPhone: input.customerPhone,
      services: bookedServices(services),
      start: input.start,
      end,
      durationMin,
      totalPrice,
      status: "confirmed",
      holdExpiresAt: null,
      note: input.note,
      source: "manual",
      externalIds: {},
      anyStylist: false,
      dateKey,
      remindersSent: { h24: false, h2: false, review: false },
      reviewed: false,
    };
    return result;
  });
  await afterBookingChange(appointment, { kind: "manual" }, caller.uid);
  return appointment;
}

/**
 * Blokiranje vremena ili odsustvo (godišnji odmor, bolovanje). Upisuje se u timeOff i, u istoj
 * transakciji, u dokumente svih dana koje pokriva — slotovi odmah nestaju.
 */
export async function blockTime(
  caller: Caller,
  input: Input<"blockTime">,
  nowMs = Date.now(),
): Promise<{ timeOffId: string; conflicts: string[] }> {
  assertCanManageStylist(caller, input.salonId, input.stylistId);
  const salon = await loadSalon(input.salonId);
  await loadStylist(input.salonId, input.stylistId);
  const ref = col.timeOff(input.salonId, input.stylistId).doc();
  const keys = dateKeysInRange(input.start, input.end, salon.timezone);

  const conflicts = await db.runTransaction(async (tx) => {
    const refs = keys.map((k) => dayRef(input.salonId, input.stylistId, k));
    const days = await readDaysTx(tx, refs);
    const interval: BusyInterval = { id: ref.id, start: input.start, end: input.end, kind: "off" };
    const overlapping: string[] = [];
    for (const r of refs) {
      const busy = days.get(r.path) ?? [];
      for (const b of busy) {
        if ((b.kind === "appt" || b.kind === "hold") && isBusyActive(b, nowMs) && b.start < input.end && input.start < b.end) {
          overlapping.push(b.id);
        }
      }
      writeDay(tx, r, withInterval(busy, interval, nowMs));
    }
    tx.create(ref, {
      start: Timestamp.fromMillis(input.start),
      end: Timestamp.fromMillis(input.end),
      kind: input.kind,
      reason: input.reason,
      createdBy: caller.uid,
      createdAt: FieldValue.serverTimestamp(),
    });
    return [...new Set(overlapping)];
  });
  return { timeOffId: ref.id, conflicts };
}

/** Uklanja odsustvo/blokadu; vraća dane koji su time oslobođeni (za listu čekanja). */
export async function removeTimeOff(caller: Caller, input: Input<"removeTimeOff">, nowMs = Date.now()): Promise<string[]> {
  assertCanManageStylist(caller, input.salonId, input.stylistId);
  const salon = await loadSalon(input.salonId);
  const ref = col.timeOff(input.salonId, input.stylistId).doc(input.timeOffId);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return [];
    const start = (snap.data()?.start as Timestamp).toMillis();
    const end = (snap.data()?.end as Timestamp).toMillis();
    const refs = dateKeysInRange(start, end, salon.timezone).map((k) => dayRef(input.salonId, input.stylistId, k));
    const days = await readDaysTx(tx, refs);
    for (const r of refs) {
      writeDay(tx, r, withoutInterval(days.get(r.path) ?? [], input.timeOffId, nowMs));
    }
    tx.delete(ref);
    const today = dateKeyOf(nowMs, salon.timezone);
    return dateKeysInRange(start, end, salon.timezone).filter((k) => k >= today);
  });
}
