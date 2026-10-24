import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../common/firebase";
import { AppError, notAllowed } from "../common/errors";
import type { Caller } from "../common/caller";
import { isStaffOf } from "../common/caller";
import type { Input } from "../common/validation";
import type { AppointmentDoc } from "../common/repo";
import { col, loadAppointment, loadSalon, loadServices, loadStylists, toAppointment } from "../common/repo";
import { fitsDay, pickLeastLoaded, withinBookingWindow, type StylistDay } from "../domain/availability";
import { canCustomerChange, canStaffTransition, cancellationDeadline, canMarkOutcome } from "../domain/policy";
import { dateKeyOf } from "../domain/time";
import type { AppointmentStatus, Service, Stylist } from "../domain/types";
import { externalBusy } from "../integrations/busy";
import { markWaitlistBooked } from "../waitlist";
import { buildStylistDay } from "./availabilityService";
import { dayRef, readDaysTx, withInterval, withoutInterval, writeDay } from "./days";
import { afterBookingChange } from "./effects";
import { refreshedSlots } from "./hold";

type Actor = "customer" | "staff";

function actorFor(caller: Caller, appointment: AppointmentDoc): Actor {
  if (appointment.customerUid === caller.uid) return "customer";
  if (isStaffOf(caller, appointment.salonId)) {
    // Frizer upravlja samo svojim terminima; vlasnik svim terminima salona.
    if (caller.roles.includes("owner") || caller.stylistId === appointment.stylistId) return "staff";
  }
  throw notAllowed("Not your appointment");
}

function policyError(salonPhone: string, deadline: number): AppError {
  return new AppError("POLICY_VIOLATION", "Cancellation deadline passed", {
    reason: "CANCEL_DEADLINE",
    salonPhone,
    deadline,
  });
}

// ---------------- Potvrda ----------------

export async function confirmBooking(caller: Caller, input: Input<"confirmBooking">, nowMs = Date.now()): Promise<AppointmentDoc> {
  const preview = await loadAppointment(input.appointmentId);
  if (preview.customerUid !== caller.uid) throw notAllowed("Not your appointment");
  const salon = await loadSalon(preview.salonId);
  const stylists = await loadStylists(preview.salonId);
  const stylist = stylists.find((s) => s.id === preview.stylistId);
  if (!stylist) throw new AppError("NOT_FOUND", "Stylist not found");
  const external = await externalBusy(salon.id, [stylist], salon.timezone, nowMs);

  const confirmed = await db.runTransaction(async (tx) => {
    const ref = col.appointments().doc(input.appointmentId);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new AppError("SLOT_TAKEN", "Hold expired", { reason: "HOLD_EXPIRED" });
    const appointment = toAppointment(snap);
    if (appointment.status === "confirmed") return appointment;
    if (appointment.status !== "held") throw new AppError("POLICY_VIOLATION", "Appointment is not on hold");

    const ref2 = dayRef(appointment.salonId, appointment.stylistId, appointment.dateKey);
    const days = await readDaysTx(tx, [ref2]);
    const busy = days.get(ref2.path) ?? [];

    if ((appointment.holdExpiresAt ?? 0) <= nowMs) {
      // Hold je istekao: termin je i dalje naš samo ako ga u međuvremenu niko nije zauzeo.
      const day = buildStylistDay(salon, stylist, appointment.dateKey, busy, external.get(stylist.id) ?? []);
      const params = { dateKey: appointment.dateKey, zone: salon.timezone, durationMin: appointment.durationMin, rules: salon.rules, nowMs };
      if (!fitsDay(appointment.start, day, params, { excludeId: appointment.id })) return null;
    }
    writeDay(
      tx,
      ref2,
      withInterval(busy, { id: appointment.id, start: appointment.start, end: appointment.end, kind: "appt" }, nowMs),
    );
    tx.update(ref, {
      status: "confirmed",
      holdExpiresAt: null,
      note: input.note,
      confirmedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { ...appointment, status: "confirmed" as const, holdExpiresAt: null, note: input.note };
  });

  if (!confirmed) {
    await col.appointments().doc(input.appointmentId).delete();
    const day = await refreshedSlots(preview.salonId, preview.services.map((s) => s.id), preview.anyStylist ? null : preview.stylistId, preview.dateKey, nowMs);
    throw new AppError("SLOT_TAKEN", "Hold expired and the slot was taken", { date: day.date, slots: day.slots });
  }
  await markWaitlistBooked(caller.uid, confirmed.salonId, confirmed.dateKey);
  await afterBookingChange(confirmed, { kind: "confirmed", actor: "customer" }, caller.uid);
  return confirmed;
}

// ---------------- Odustajanje od hold-a ----------------

export async function releaseHold(caller: Caller, input: Input<"releaseHold">, nowMs = Date.now()): Promise<void> {
  await db.runTransaction(async (tx) => {
    const ref = col.appointments().doc(input.appointmentId);
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const appointment = toAppointment(snap);
    if (appointment.customerUid !== caller.uid) throw notAllowed("Not your hold");
    if (appointment.status !== "held") return;
    const ref2 = dayRef(appointment.salonId, appointment.stylistId, appointment.dateKey);
    const days = await readDaysTx(tx, [ref2]);
    writeDay(tx, ref2, withoutInterval(days.get(ref2.path) ?? [], appointment.id, nowMs));
    tx.delete(ref);
  });
}

// ---------------- Otkazivanje ----------------

export async function cancelAppointment(
  caller: Caller,
  input: Input<"cancelAppointment">,
  nowMs = Date.now(),
  options: { bypassDeadline?: boolean } = {},
): Promise<AppointmentDoc> {
  const preview = await loadAppointment(input.appointmentId);
  const actor = actorFor(caller, preview);
  if (preview.status === "held" && actor === "customer") {
    await releaseHold(caller, { appointmentId: preview.id }, nowMs);
    return { ...preview, status: "canceled_by_customer" };
  }
  const salon = await loadSalon(preview.salonId);

  const result = await db.runTransaction(async (tx) => {
    const ref = col.appointments().doc(input.appointmentId);
    const snap = await tx.get(ref);
    const appointment = toAppointment(snap);
    if (appointment.status !== "confirmed" && appointment.status !== "held") {
      throw new AppError("POLICY_VIOLATION", "Appointment cannot be canceled", { reason: "NOT_ACTIVE" });
    }
    if (actor === "customer" && !options.bypassDeadline && !canCustomerChange(appointment.start, salon.rules, nowMs)) {
      throw policyError(salon.phone, cancellationDeadline(appointment.start, salon.rules));
    }
    const ref2 = dayRef(appointment.salonId, appointment.stylistId, appointment.dateKey);
    const days = await readDaysTx(tx, [ref2]);
    writeDay(tx, ref2, withoutInterval(days.get(ref2.path) ?? [], appointment.id, nowMs));
    const status: AppointmentStatus = actor === "customer" ? "canceled_by_customer" : "canceled_by_salon";
    tx.update(ref, {
      status,
      holdExpiresAt: null,
      cancelReason: input.reason,
      canceledAt: FieldValue.serverTimestamp(),
      canceledBy: caller.uid,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { previous: appointment, updated: { ...appointment, status } };
  });

  await afterBookingChange(result.updated, { kind: "canceled", actor, previous: result.previous }, caller.uid);
  return result.updated;
}

// ---------------- Promena termina ----------------

function candidatesForReschedule(
  appointment: AppointmentDoc,
  stylists: Stylist[],
  services: Service[] | null,
  requested: string | null,
): Stylist[] {
  const active = stylists.filter((s) => s.active);
  const capable = services
    ? active.filter((s) => services.every((svc) => svc.stylistIds.includes(s.id)))
    : active.filter((s) => s.id === appointment.stylistId);
  if (requested) {
    const one = capable.find((s) => s.id === requested);
    if (!one) throw new AppError("VALIDATION", "Stylist does not perform these services");
    return [one];
  }
  if (appointment.anyStylist) return capable;
  return capable.filter((s) => s.id === appointment.stylistId);
}

export async function rescheduleAppointment(
  caller: Caller,
  input: Input<"rescheduleAppointment">,
  nowMs = Date.now(),
): Promise<AppointmentDoc> {
  const preview = await loadAppointment(input.appointmentId);
  const actor = actorFor(caller, preview);
  const salon = await loadSalon(preview.salonId);
  const stylists = await loadStylists(preview.salonId);
  const services = await loadServices(preview.salonId, preview.services.map((s) => s.id)).catch(() => null);
  const candidates = candidatesForReschedule(preview, stylists, services, input.stylistId);
  if (candidates.length === 0) throw new AppError("VALIDATION", "No stylist available for these services");

  const zone = salon.timezone;
  const newDateKey = dateKeyOf(input.start, zone);
  if (actor === "customer" && !withinBookingWindow(input.start, salon.rules, nowMs, zone)) {
    throw new AppError("POLICY_VIOLATION", "Slot is outside the booking window", { reason: "OUTSIDE_WINDOW" });
  }
  if (actor === "staff" && input.start < nowMs) {
    throw new AppError("POLICY_VIOLATION", "Cannot move an appointment into the past", { reason: "OUTSIDE_WINDOW" });
  }
  const external = await externalBusy(salon.id, candidates, zone, nowMs);
  const order = new Map(stylists.map((s) => [s.id, s.order]));

  const result = await db.runTransaction(async (tx) => {
    const ref = col.appointments().doc(input.appointmentId);
    const snap = await tx.get(ref);
    const appointment = toAppointment(snap);
    if (appointment.status !== "confirmed") {
      throw new AppError("POLICY_VIOLATION", "Only confirmed appointments can be moved", { reason: "NOT_ACTIVE" });
    }
    if (actor === "customer" && !canCustomerChange(appointment.start, salon.rules, nowMs)) {
      throw policyError(salon.phone, cancellationDeadline(appointment.start, salon.rules));
    }
    const oldRef = dayRef(appointment.salonId, appointment.stylistId, appointment.dateKey);
    const newRefs = candidates.map((c) => dayRef(salon.id, c.id, newDateKey));
    const days = await readDaysTx(tx, [oldRef, ...newRefs]);

    const params = { dateKey: newDateKey, zone, durationMin: appointment.durationMin, rules: salon.rules, nowMs };
    const free: StylistDay[] = [];
    candidates.forEach((stylist, i) => {
      const busy = (days.get(newRefs[i].path) ?? []).filter((b) => b.id !== appointment.id);
      const day = buildStylistDay(salon, stylist, newDateKey, busy, external.get(stylist.id) ?? []);
      if (fitsDay(input.start, day, params, { ignoreWorkingHours: false })) free.push(day);
    });
    const chosen = pickLeastLoaded(free, nowMs, order);
    if (!chosen) return null;
    const stylist = candidates.find((c) => c.id === chosen.stylistId)!;
    const newRef = dayRef(salon.id, stylist.id, newDateKey);
    const end = input.start + appointment.durationMin * 60_000;

    if (oldRef.path !== newRef.path) {
      writeDay(tx, oldRef, withoutInterval(days.get(oldRef.path) ?? [], appointment.id, nowMs));
    }
    const base = (days.get(newRef.path) ?? []).filter((b) => b.id !== appointment.id);
    writeDay(tx, newRef, withInterval(base, { id: appointment.id, start: input.start, end, kind: "appt" }, nowMs));
    tx.update(ref, {
      stylistId: stylist.id,
      stylistName: stylist.name,
      start: Timestamp.fromMillis(input.start),
      end: Timestamp.fromMillis(end),
      dateKey: newDateKey,
      remindersSent: { h24: false, h2: false, review: false },
      rescheduledAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return {
      previous: appointment,
      updated: { ...appointment, stylistId: stylist.id, stylistName: stylist.name, start: input.start, end, dateKey: newDateKey },
    };
  });

  if (!result) {
    const day = await refreshedSlots(salon.id, preview.services.map((s) => s.id), input.stylistId, newDateKey, nowMs).catch(
      () => ({ date: newDateKey, slots: [] }),
    );
    throw new AppError("SLOT_TAKEN", "Slot was just taken", { date: day.date, slots: day.slots });
  }
  await afterBookingChange(result.updated, { kind: "rescheduled", actor, previous: result.previous }, caller.uid);
  return result.updated;
}

// ---------------- Status (osoblje) ----------------

export async function updateAppointmentStatus(
  caller: Caller,
  input: Input<"updateAppointmentStatus">,
  nowMs = Date.now(),
): Promise<AppointmentDoc> {
  const preview = await loadAppointment(input.appointmentId);
  const actor = actorFor(caller, preview);
  if (actor !== "staff") throw notAllowed("Only salon staff can change the status");
  if (input.status === "canceled_by_salon") {
    return cancelAppointment(caller, { appointmentId: input.appointmentId, reason: input.reason }, nowMs);
  }

  const result = await db.runTransaction(async (tx) => {
    const ref = col.appointments().doc(input.appointmentId);
    const appointment = toAppointment(await tx.get(ref));
    if (!canStaffTransition(appointment.status, input.status)) {
      throw new AppError("POLICY_VIOLATION", "Status change not allowed", { reason: "BAD_TRANSITION" });
    }
    if ((input.status === "completed" || input.status === "no_show") && !canMarkOutcome(appointment.start, nowMs)) {
      throw new AppError("POLICY_VIOLATION", "Appointment has not started yet", { reason: "NOT_STARTED" });
    }
    if (appointment.status === "held" && input.status === "confirmed") {
      const ref2 = dayRef(appointment.salonId, appointment.stylistId, appointment.dateKey);
      const days = await readDaysTx(tx, [ref2]);
      writeDay(
        tx,
        ref2,
        withInterval(days.get(ref2.path) ?? [], { id: appointment.id, start: appointment.start, end: appointment.end, kind: "appt" }, nowMs),
      );
    }
    tx.update(ref, {
      status: input.status,
      holdExpiresAt: null,
      statusChangedBy: caller.uid,
      updatedAt: FieldValue.serverTimestamp(),
      ...(input.status === "completed" ? { completedAt: FieldValue.serverTimestamp() } : {}),
    });
    return { before: appointment.status, updated: { ...appointment, status: input.status, holdExpiresAt: null } };
  });

  if (result.before === "held" && result.updated.status === "confirmed") {
    await afterBookingChange(result.updated, { kind: "confirmed", actor: "staff" }, caller.uid);
  }
  return result.updated;
}
