import { DateTime } from "luxon";
import { DEFAULT_ZONE } from "../domain/time";

export type Language = "sr" | "en";

export type NotificationType =
  | "booking_confirmed"
  | "booking_changed"
  | "booking_canceled_by_salon"
  | "reminder_24h"
  | "reminder_2h"
  | "review_request"
  | "waitlist_slot"
  | "staff_new_booking"
  | "staff_canceled"
  | "staff_rescheduled"
  | "review_reply"
  | "stylist_invited";

export interface TemplateParams {
  salonName: string;
  /** Početak termina (ms), ako se obaveštenje odnosi na termin. */
  start?: number;
  zone?: string;
  customerName?: string;
  stylistName?: string;
  /** Lokalni datum (GGGG-MM-DD) za listu čekanja. */
  dateKey?: string;
}

export function formatDay(ms: number, language: Language, zone = DEFAULT_ZONE): string {
  const dt = DateTime.fromMillis(ms, { zone }).setLocale(language === "sr" ? "sr-Latn" : "en-GB");
  return language === "sr" ? dt.toFormat("cccc, d. MMMM") : dt.toFormat("cccc, d MMMM");
}

export function formatTime(ms: number, zone = DEFAULT_ZONE): string {
  return DateTime.fromMillis(ms, { zone }).toFormat("HH:mm");
}

function dayOfKey(dateKey: string, language: Language, zone: string): string {
  return formatDay(DateTime.fromISO(dateKey, { zone }).toMillis(), language, zone);
}

/** Kanal obaveštenja na Android-u (mora da postoji u aplikaciji). */
export function channelFor(type: NotificationType): "appointments" | "reminders" | "waitlist" {
  if (type === "reminder_24h" || type === "reminder_2h" || type === "review_request") return "reminders";
  if (type === "waitlist_slot") return "waitlist";
  return "appointments";
}

export function render(type: NotificationType, language: Language, p: TemplateParams): { title: string; body: string } {
  const zone = p.zone ?? DEFAULT_ZONE;
  const when = p.start !== undefined ? `${formatDay(p.start, language, zone)}, ${formatTime(p.start, zone)}` : "";
  const sr = language === "sr";
  switch (type) {
    case "booking_confirmed":
      return sr
        ? { title: "Termin je potvrđen", body: `${p.salonName} te čeka ${when}.` }
        : { title: "Your appointment is confirmed", body: `${p.salonName} is expecting you ${when}.` };
    case "booking_changed":
      return sr
        ? { title: "Termin je promenjen", body: `Novi termin u salonu ${p.salonName}: ${when}.` }
        : { title: "Your appointment was changed", body: `New time at ${p.salonName}: ${when}.` };
    case "booking_canceled_by_salon":
      return sr
        ? { title: "Salon je otkazao termin", body: `${p.salonName} je otkazao tvoj termin (${when}). Izvini zbog promene.` }
        : { title: "The salon canceled your appointment", body: `${p.salonName} canceled your appointment (${when}).` };
    case "reminder_24h":
      return sr
        ? { title: "Sutra imaš termin", body: `${p.salonName}, ${when}.` }
        : { title: "Your appointment is tomorrow", body: `${p.salonName}, ${when}.` };
    case "reminder_2h":
      return sr
        ? { title: "Termin za 2 sata", body: `${p.salonName} u ${p.start !== undefined ? formatTime(p.start, zone) : ""}. Vidimo se!` }
        : { title: "Appointment in 2 hours", body: `${p.salonName} at ${p.start !== undefined ? formatTime(p.start, zone) : ""}. See you!` };
    case "review_request":
      return sr
        ? { title: "Kako je bilo?", body: `Oceni posetu salonu ${p.salonName}. Treba ti manje od minut.` }
        : { title: "How was it?", body: `Rate your visit to ${p.salonName}. It takes less than a minute.` };
    case "waitlist_slot": {
      const day = p.dateKey ? dayOfKey(p.dateKey, language, zone) : "";
      return sr
        ? { title: "Oslobodio se termin", body: `${p.salonName} ima slobodan termin (${day}). Ko prvi potvrdi, njegov je.` }
        : { title: "A slot just opened up", body: `${p.salonName} has a free slot (${day}). First to confirm gets it.` };
    }
    case "staff_new_booking":
      return sr
        ? { title: "Novi termin", body: `${p.customerName ?? ""} · ${p.stylistName ?? ""} · ${when}` }
        : { title: "New booking", body: `${p.customerName ?? ""} · ${p.stylistName ?? ""} · ${when}` };
    case "staff_canceled":
      return sr
        ? { title: "Termin otkazan", body: `${p.customerName ?? ""} je otkazao/la termin ${when}.` }
        : { title: "Booking canceled", body: `${p.customerName ?? ""} canceled the appointment ${when}.` };
    case "staff_rescheduled":
      return sr
        ? { title: "Termin pomeren", body: `${p.customerName ?? ""} · novi termin ${when}` }
        : { title: "Booking moved", body: `${p.customerName ?? ""} · new time ${when}` };
    case "review_reply":
      return sr
        ? { title: "Salon ti je odgovorio", body: `${p.salonName} je odgovorio na tvoju recenziju.` }
        : { title: "The salon replied", body: `${p.salonName} replied to your review.` };
    case "stylist_invited":
      return sr
        ? { title: "Dobrodošao/la u tim", body: `${p.salonName} te je dodao kao frizera. Tvoj raspored je spreman.` }
        : { title: "Welcome to the team", body: `${p.salonName} added you as a stylist. Your schedule is ready.` };
  }
}
