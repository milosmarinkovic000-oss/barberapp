import type { AppointmentStatus, SalonRules } from "./types";

const HOUR = 3_600_000;

/** Poslednji trenutak u kom klijent sme sam da otkaže ili promeni termin. */
export function cancellationDeadline(startMs: number, rules: SalonRules): number {
  return startMs - rules.cancelHours * HOUR;
}

export function canCustomerChange(startMs: number, rules: SalonRules, nowMs: number): boolean {
  return nowMs <= cancellationDeadline(startMs, rules);
}

/** Dozvoljeni prelazi statusa koje radi osoblje salona. */
const STAFF_TRANSITIONS: Record<AppointmentStatus, readonly AppointmentStatus[]> = {
  held: ["confirmed", "canceled_by_salon"],
  confirmed: ["completed", "no_show", "canceled_by_salon"],
  completed: ["confirmed", "no_show"],
  no_show: ["confirmed", "completed"],
  canceled_by_customer: [],
  canceled_by_salon: [],
};

export function canStaffTransition(from: AppointmentStatus, to: AppointmentStatus): boolean {
  return STAFF_TRANSITIONS[from].includes(to);
}

/** Termin koji zauzima vreme frizera. */
export function occupiesTime(status: AppointmentStatus): boolean {
  return status === "held" || status === "confirmed" || status === "completed" || status === "no_show";
}

/** „Završeno“ i „nije došao“ smeju da se označe tek kad termin počne. */
export function canMarkOutcome(startMs: number, nowMs: number): boolean {
  return nowMs >= startMs;
}

export const MAX_ACTIVE_HOLDS = 2;
export const HOLD_MINUTES = 5;
