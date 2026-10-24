import { logger } from "firebase-functions";
import type { AppointmentDoc } from "../common/repo";
import { loadSalon, loadStylist } from "../common/repo";
import { syncAppointment } from "../integrations/sync";
import { notifyStaff, notifyUser } from "../notifications/notify";
import { notifyWaitlist } from "../waitlist";

export type BookingChange =
  | { kind: "confirmed"; actor: "customer" | "staff" | "external" }
  | { kind: "canceled"; actor: "customer" | "staff" | "external"; previous: AppointmentDoc }
  | { kind: "rescheduled"; actor: "customer" | "staff" | "external"; previous: AppointmentDoc }
  | { kind: "manual" };

/**
 * Posledice promene termina, posle uspešne transakcije: obaveštenja, spoljni kalendari i lista
 * čekanja. Nikad ne bacaju grešku nazad korisniku — termin je već upisan.
 */
export async function afterBookingChange(appointment: AppointmentDoc, change: BookingChange, actorUid: string | null): Promise<void> {
  try {
    const salon = await loadSalon(appointment.salonId);
    const stylist = await loadStylist(appointment.salonId, appointment.stylistId).catch(() => null);
    const params = {
      salonName: salon.name,
      start: appointment.start,
      zone: salon.timezone,
      customerName: appointment.customerName,
      stylistName: appointment.stylistName,
    };
    const data = { appointmentId: appointment.id, salonId: salon.id };
    const tasks: Promise<unknown>[] = [];

    switch (change.kind) {
      case "confirmed":
      case "manual":
        if (appointment.customerUid) tasks.push(notifyUser(appointment.customerUid, "booking_confirmed", params, data));
        tasks.push(notifyStaff(salon, stylist?.uid ?? null, "staff_new_booking", params, data, actorUid));
        if (stylist) tasks.push(syncAppointment(appointment, "upsert", salon, stylist));
        break;
      case "canceled":
        if (change.actor !== "customer" && appointment.customerUid) {
          tasks.push(notifyUser(appointment.customerUid, "booking_canceled_by_salon", params, data));
        }
        if (change.actor !== "staff") {
          tasks.push(notifyStaff(salon, stylist?.uid ?? null, "staff_canceled", params, data, actorUid));
        }
        if (stylist) tasks.push(syncAppointment(change.previous, "delete", salon, stylist));
        tasks.push(notifyWaitlist(salon.id, change.previous.dateKey, actorUid));
        break;
      case "rescheduled": {
        if (change.actor !== "customer" && appointment.customerUid) {
          tasks.push(notifyUser(appointment.customerUid, "booking_changed", params, data));
        }
        if (change.actor !== "staff") {
          tasks.push(notifyStaff(salon, stylist?.uid ?? null, "staff_rescheduled", params, data, actorUid));
        }
        if (change.previous.stylistId !== appointment.stylistId) {
          const previousStylist = await loadStylist(salon.id, change.previous.stylistId).catch(() => null);
          if (previousStylist) tasks.push(syncAppointment(change.previous, "delete", salon, previousStylist));
          if (stylist) tasks.push(syncAppointment({ ...appointment, externalIds: {} }, "upsert", salon, stylist));
        } else if (stylist) {
          tasks.push(syncAppointment(appointment, "upsert", salon, stylist));
        }
        tasks.push(notifyWaitlist(salon.id, change.previous.dateKey, actorUid));
        break;
      }
    }
    await Promise.all(tasks);
  } catch (error) {
    logger.error("afterBookingChange failed", { appointmentId: appointment.id, error: String(error) });
  }
}
