import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../common/firebase";
import { AppError } from "../common/errors";
import type { Caller } from "../common/caller";
import type { Input } from "../common/validation";
import { bookedServices, col, loadSalon, loadServices, loadStylists, toAppointment, totals } from "../common/repo";
import { fitsDay, pickLeastLoaded, withinBookingWindow, type StylistDay } from "../domain/availability";
import { HOLD_MINUTES, MAX_ACTIVE_HOLDS } from "../domain/policy";
import { dateKeyOf } from "../domain/time";
import type { BusyInterval } from "../domain/types";
import { externalBusy } from "../integrations/busy";
import { computeAvailability } from "./availabilityService";
import { buildStylistDay, candidateStylists } from "./availabilityService";
import { dayRef, readDaysTx, withInterval, withoutInterval, writeDay } from "./days";

export interface HoldResult {
  appointmentId: string;
  stylistId: string;
  stylistName: string;
  start: number;
  end: number;
  holdExpiresAt: number;
  durationMin: number;
  totalPrice: number;
}

/** Osveženi slotovi za dan, šalju se uz grešku SLOT_TAKEN. */
export async function refreshedSlots(
  salonId: string,
  serviceIds: string[],
  stylistId: string | null,
  dateKey: string,
  nowMs: number,
): Promise<{ date: string; slots: { start: number; stylistIds: string[] }[] }> {
  const result = await computeAvailability({ salonId, serviceIds, stylistId, fromDate: dateKey, days: 1 }, nowMs);
  return result.days[0] ?? { date: dateKey, slots: [] };
}

/**
 * Zaključava slot na 5 minuta. Transakcija čita i upisuje dokument dana izabranog frizera
 * (stylists/{id}/days/{datum}), pa od dva istovremena zahteva za isti slot uspeva samo jedan;
 * drugi dobija SLOT_TAKEN i osvežene slotove.
 */
export async function holdSlot(caller: Caller, input: Input<"holdSlot">, nowMs = Date.now()): Promise<HoldResult> {
  const salon = await loadSalon(input.salonId);
  if (!salon.published) throw new AppError("NOT_FOUND", "Salon not found");
  const [services, stylists, userSnap] = await Promise.all([
    loadServices(input.salonId, input.serviceIds),
    loadStylists(input.salonId),
    col.users().doc(caller.uid).get(),
  ]);
  const candidates = candidateStylists(stylists, services, input.stylistId);
  if (candidates.length === 0) throw new AppError("VALIDATION", "No stylist performs these services");

  const { durationMin, totalPrice } = totals(services);
  const zone = salon.timezone;
  const dateKey = dateKeyOf(input.start, zone);
  if (!withinBookingWindow(input.start, salon.rules, nowMs, zone)) {
    throw new AppError("POLICY_VIOLATION", "Slot is outside the booking window", { reason: "OUTSIDE_WINDOW" });
  }

  const external = await externalBusy(salon.id, candidates, zone, nowMs);
  const order = new Map(stylists.map((s) => [s.id, s.order]));
  const user = userSnap.data() ?? {};
  const customerName = typeof user.name === "string" && user.name.length > 0 ? user.name : (caller.displayName ?? "");
  const appointmentRef = col.appointments().doc();
  const params = { dateKey, zone, durationMin, rules: salon.rules, nowMs };

  const outcome = await db.runTransaction(async (tx) => {
    // --- čitanja (sva pre upisa) ---
    const replaced = input.replaceHoldId ? await tx.get(col.appointments().doc(input.replaceHoldId)) : null;
    const replacedAppt = replaced?.exists ? toAppointment(replaced) : null;
    const replacedOwn =
      replacedAppt && replacedAppt.customerUid === caller.uid && replacedAppt.status === "held" ? replacedAppt : null;
    const replacedDayRef = replacedOwn ? dayRef(replacedOwn.salonId, replacedOwn.stylistId, replacedOwn.dateKey) : null;

    const refs = candidates.map((c) => dayRef(salon.id, c.id, dateKey));
    const days = await readDaysTx(tx, replacedDayRef ? [...refs, replacedDayRef] : refs);

    const holds = await tx.get(
      col.appointments().where("customerUid", "==", caller.uid).where("status", "==", "held"),
    );
    const activeHolds = holds.docs
      .map(toAppointment)
      .filter((a) => (a.holdExpiresAt ?? 0) > nowMs && a.id !== replacedOwn?.id);
    if (activeHolds.length >= MAX_ACTIVE_HOLDS) {
      throw new AppError("POLICY_VIOLATION", "Too many active holds", { reason: "MAX_HOLDS" });
    }

    // Zauzeće bez hold-a koji se zamenjuje (korisnik je izabrao drugi slot).
    const busyFor = (path: string): BusyInterval[] =>
      (days.get(path) ?? []).filter((b) => b.id !== replacedOwn?.id);

    const free: StylistDay[] = [];
    candidates.forEach((stylist, i) => {
      const day = buildStylistDay(salon, stylist, dateKey, busyFor(refs[i].path), external.get(stylist.id) ?? []);
      if (fitsDay(input.start, day, params)) free.push(day);
    });
    const chosen = pickLeastLoaded(free, nowMs, order);
    if (!chosen) return null;

    // --- upisi ---
    const stylist = candidates.find((c) => c.id === chosen.stylistId)!;
    const chosenRef = dayRef(salon.id, stylist.id, dateKey);
    const holdExpiresAt = nowMs + HOLD_MINUTES * 60_000;
    const end = input.start + durationMin * 60_000;

    if (replacedOwn && replacedDayRef) {
      if (replacedDayRef.path !== chosenRef.path) {
        writeDay(tx, replacedDayRef, withoutInterval(days.get(replacedDayRef.path) ?? [], replacedOwn.id, nowMs));
      }
      tx.delete(col.appointments().doc(replacedOwn.id));
    }
    const base = (days.get(chosenRef.path) ?? []).filter((b) => b.id !== replacedOwn?.id);
    writeDay(
      tx,
      chosenRef,
      withInterval(base, { id: appointmentRef.id, start: input.start, end, kind: "hold", expiresAt: holdExpiresAt }, nowMs),
    );
    tx.create(appointmentRef, {
      salonId: salon.id,
      salonName: salon.name,
      salonPhone: salon.phone,
      salonAddress: `${salon.address}, ${salon.city}`,
      stylistId: stylist.id,
      stylistName: stylist.name,
      customerUid: caller.uid,
      customerName,
      customerPhone: caller.phone,
      services: bookedServices(services),
      start: Timestamp.fromMillis(input.start),
      end: Timestamp.fromMillis(end),
      durationMin,
      totalPrice,
      status: "held",
      holdExpiresAt: Timestamp.fromMillis(holdExpiresAt),
      note: "",
      source: "app",
      externalIds: {},
      anyStylist: input.stylistId === null,
      dateKey,
      remindersSent: { h24: false, h2: false, review: false },
      reviewed: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { stylistId: stylist.id, stylistName: stylist.name, end, holdExpiresAt };
  });

  if (!outcome) {
    const day = await refreshedSlots(salon.id, input.serviceIds, input.stylistId, dateKey, nowMs);
    throw new AppError("SLOT_TAKEN", "Slot was just taken", { date: day.date, slots: day.slots });
  }
  return {
    appointmentId: appointmentRef.id,
    stylistId: outcome.stylistId,
    stylistName: outcome.stylistName,
    start: input.start,
    end: outcome.end,
    holdExpiresAt: outcome.holdExpiresAt,
    durationMin,
    totalPrice,
  };
}
