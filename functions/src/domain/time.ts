import { DateTime } from "luxon";

export const DEFAULT_ZONE = "Europe/Belgrade";

const DATE_KEY = /^\d{4}-\d{2}-\d{2}$/;
const HHMM = /^([01]\d|2[0-3]):([0-5]\d)$|^24:00$/;

export function isDateKey(value: string): boolean {
  if (!DATE_KEY.test(value)) return false;
  return DateTime.fromISO(value, { zone: "UTC" }).isValid;
}

export function isHhMm(value: string): boolean {
  return HHMM.test(value);
}

/** Minuti od ponoći za "HH:mm". "24:00" je 1440. */
export function minutesOfDay(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

/** Lokalni datum (GGGG-MM-DD) trenutka u datoj zoni. */
export function dateKeyOf(ms: number, zone: string = DEFAULT_ZONE): string {
  return DateTime.fromMillis(ms, { zone }).toISODate() as string;
}

/** ISO dan u nedelji (1 = ponedeljak … 7 = nedelja) za lokalni datum. */
export function isoWeekday(dateKey: string, zone: string = DEFAULT_ZONE): number {
  return DateTime.fromISO(dateKey, { zone }).weekday;
}

export function addDays(dateKey: string, days: number, zone: string = DEFAULT_ZONE): string {
  return DateTime.fromISO(dateKey, { zone }).plus({ days }).toISODate() as string;
}

/** Broj dana od `from` do `to` (oba lokalni datumi). */
export function daysBetween(from: string, to: string): number {
  const a = DateTime.fromISO(from, { zone: "UTC" });
  const b = DateTime.fromISO(to, { zone: "UTC" });
  return Math.round(b.diff(a, "days").days);
}

/**
 * Pretvara lokalno vreme na zidnom satu (datum + minuti od ponoći) u UTC milisekunde.
 * Vraća null ako to lokalno vreme ne postoji (prelazak na letnje računanje vremena).
 */
export function localToUtc(dateKey: string, minutes: number, zone: string = DEFAULT_ZONE): number | null {
  const base = DateTime.fromISO(dateKey, { zone });
  if (minutes >= 24 * 60) {
    return base.plus({ days: 1 }).startOf("day").toMillis();
  }
  const hour = Math.floor(minutes / 60);
  const minute = minutes % 60;
  const dt = DateTime.fromObject(
    { year: base.year, month: base.month, day: base.day, hour, minute },
    { zone },
  );
  if (!dt.isValid || dt.hour !== hour || dt.minute !== minute) {
    return null;
  }
  return dt.toMillis();
}

/**
 * Kao localToUtc, ali za granice intervala: nepostojeće lokalno vreme pomera se na prvi
 * sledeći postojeći trenutak (npr. 02:30 na dan prelaska na letnje vreme postaje 03:00).
 */
export function localBoundaryToUtc(dateKey: string, minutes: number, zone: string = DEFAULT_ZONE): number {
  const exact = localToUtc(dateKey, minutes, zone);
  if (exact !== null) return exact;
  const base = DateTime.fromISO(dateKey, { zone });
  return DateTime.fromObject(
    { year: base.year, month: base.month, day: base.day, hour: Math.floor(minutes / 60), minute: minutes % 60 },
    { zone },
  ).toMillis();
}

export function startOfLocalDay(dateKey: string, zone: string = DEFAULT_ZONE): number {
  return DateTime.fromISO(dateKey, { zone }).startOf("day").toMillis();
}

export function endOfLocalDay(dateKey: string, zone: string = DEFAULT_ZONE): number {
  return DateTime.fromISO(dateKey, { zone }).plus({ days: 1 }).startOf("day").toMillis();
}

/** Svi lokalni datumi koje interval [start, end) dodiruje. */
export function dateKeysInRange(start: number, end: number, zone: string = DEFAULT_ZONE): string[] {
  const keys: string[] = [];
  let cursor = dateKeyOf(start, zone);
  const last = dateKeyOf(Math.max(start, end - 1), zone);
  while (cursor <= last) {
    keys.push(cursor);
    cursor = addDays(cursor, 1, zone);
  }
  return keys;
}

export function formatLocal(ms: number, format: string, zone: string = DEFAULT_ZONE, locale = "sr-Latn"): string {
  return DateTime.fromMillis(ms, { zone }).setLocale(locale).toFormat(format);
}
