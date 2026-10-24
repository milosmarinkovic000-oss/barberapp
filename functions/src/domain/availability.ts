import type { BusyInterval, DaySlots, SalonRules, Slot, TimeRange, WeeklyHours } from "./types";
import { addDays, dateKeyOf, isoWeekday, localBoundaryToUtc, localToUtc, minutesOfDay } from "./time";

const MINUTE = 60_000;

export interface StylistDay {
  stylistId: string;
  /** Radno vreme frizera tog dana, već presečeno sa radnim vremenom salona. */
  hours: TimeRange[];
  breaks: TimeRange[];
  busy: BusyInterval[];
}

export interface DayParams {
  dateKey: string;
  zone: string;
  durationMin: number;
  rules: SalonRules;
  nowMs: number;
}

interface MsRange {
  start: number;
  end: number;
}

function dayKey(weekday: number): "1" | "2" | "3" | "4" | "5" | "6" | "7" {
  return String(weekday) as "1" | "2" | "3" | "4" | "5" | "6" | "7";
}

/** Presek dva skupa lokalnih intervala (u minutima od ponoći). */
function intersectRanges(a: TimeRange[], b: TimeRange[]): TimeRange[] {
  const result: TimeRange[] = [];
  for (const x of a) {
    for (const y of b) {
      const start = Math.max(minutesOfDay(x.start), minutesOfDay(y.start));
      const end = Math.min(minutesOfDay(x.end), minutesOfDay(y.end));
      if (end > start) {
        result.push({ start: toHhMm(start), end: toHhMm(end) });
      }
    }
  }
  return result.sort((p, q) => minutesOfDay(p.start) - minutesOfDay(q.start));
}

function toHhMm(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

/**
 * Radno vreme frizera za dati dan. Frizer bez sopstvenog rasporeda radi po radnom vremenu
 * salona; frizer sa rasporedom radi samo u delu koji se poklapa sa radnim vremenom salona.
 */
export function effectiveHours(
  salonHours: WeeklyHours,
  stylistHours: WeeklyHours | null,
  dateKey: string,
  zone: string,
): TimeRange[] {
  const key = dayKey(isoWeekday(dateKey, zone));
  const salon = salonHours[key] ?? [];
  if (stylistHours === null) return [...salon];
  const own = stylistHours[key] ?? [];
  return intersectRanges(salon, own);
}

export function breaksFor(breaks: WeeklyHours, dateKey: string, zone: string): TimeRange[] {
  return breaks[dayKey(isoWeekday(dateKey, zone))] ?? [];
}

/** Da li je interval aktivan u trenutku `nowMs` (istekli hold se ignoriše). */
export function isBusyActive(interval: BusyInterval, nowMs: number): boolean {
  if (interval.kind === "hold") {
    return interval.expiresAt !== undefined && interval.expiresAt > nowMs;
  }
  return true;
}

/** Zauzeće sa pauzom posle termina (pauza važi posle termina i hold-ova). */
function effectiveBusy(busy: BusyInterval[], bufferMin: number, nowMs: number, excludeId?: string): MsRange[] {
  return busy
    .filter((b) => b.id !== excludeId && isBusyActive(b, nowMs))
    .map((b) => ({
      start: b.start,
      end: b.kind === "appt" || b.kind === "hold" ? b.end + bufferMin * MINUTE : b.end,
    }));
}

function overlaps(a: MsRange, b: MsRange): boolean {
  return a.start < b.end && b.start < a.end;
}

/** Poslednji dan (uključivo) koji može da se zakaže. */
export function lastBookableDate(nowMs: number, rules: SalonRules, zone: string): string {
  return addDays(dateKeyOf(nowMs, zone), Math.max(rules.horizonDays, 1) - 1, zone);
}

/** Najkraće vreme do termina i horizont zakazivanja. */
export function withinBookingWindow(startMs: number, rules: SalonRules, nowMs: number, zone: string): boolean {
  if (startMs < nowMs + rules.minNoticeMinutes * MINUTE) return false;
  return dateKeyOf(startMs, zone) <= lastBookableDate(nowMs, rules, zone);
}

function workingWindows(day: StylistDay, params: DayParams): MsRange[] {
  return day.hours.map((r) => ({
    start: localBoundaryToUtc(params.dateKey, minutesOfDay(r.start), params.zone),
    end: localBoundaryToUtc(params.dateKey, minutesOfDay(r.end), params.zone),
  }));
}

function breakRanges(day: StylistDay, params: DayParams): MsRange[] {
  return day.breaks.map((r) => ({
    start: localBoundaryToUtc(params.dateKey, minutesOfDay(r.start), params.zone),
    end: localBoundaryToUtc(params.dateKey, minutesOfDay(r.end), params.zone),
  }));
}

/**
 * Proverava da li ceo interval [start, start + trajanje + pauza) staje u radno vreme
 * frizera i ne preklapa se sa pauzama ni sa zauzećem.
 */
export function fitsDay(
  startMs: number,
  day: StylistDay,
  params: DayParams,
  options: { excludeId?: string; ignoreWorkingHours?: boolean } = {},
): boolean {
  const candidate: MsRange = {
    start: startMs,
    end: startMs + (params.durationMin + params.rules.bufferMin) * MINUTE,
  };
  if (!options.ignoreWorkingHours) {
    const inside = workingWindows(day, params).some((w) => candidate.start >= w.start && candidate.end <= w.end);
    if (!inside) return false;
    if (breakRanges(day, params).some((b) => overlaps(candidate, b))) return false;
  }
  const busy = effectiveBusy(day.busy, params.rules.bufferMin, params.nowMs, options.excludeId);
  return !busy.some((b) => overlaps(candidate, b));
}

/** Svi slobodni početci za jednog frizera u jednom danu, rastuće. */
export function stylistDaySlots(day: StylistDay, params: DayParams): number[] {
  const { rules, nowMs, zone, dateKey } = params;
  const today = dateKeyOf(nowMs, zone);
  if (dateKey < today || dateKey > lastBookableDate(nowMs, rules, zone)) return [];

  const step = Math.max(rules.slotStepMin, 5);
  const span = params.durationMin + rules.bufferMin;
  const result = new Set<number>();

  for (const range of day.hours) {
    const from = minutesOfDay(range.start);
    const to = minutesOfDay(range.end);
    const first = Math.ceil(from / step) * step;
    for (let m = first; m + span <= to; m += step) {
      const start = localToUtc(dateKey, m, zone);
      if (start === null) continue;
      if (!withinBookingWindow(start, rules, nowMs, zone)) continue;
      if (fitsDay(start, day, params)) result.add(start);
    }
  }
  return [...result].sort((a, b) => a - b);
}

/** Unija slotova više frizera; svaki slot pamti koji frizeri su tada slobodni. */
export function mergeSlots(perStylist: Map<string, number[]>): Slot[] {
  const byStart = new Map<number, string[]>();
  for (const [stylistId, starts] of perStylist) {
    for (const start of starts) {
      const list = byStart.get(start) ?? [];
      list.push(stylistId);
      byStart.set(start, list);
    }
  }
  return [...byStart.entries()]
    .sort(([a], [b]) => a - b)
    .map(([start, stylistIds]) => ({ start, stylistIds: stylistIds.sort() }));
}

export function daySlots(days: StylistDay[], params: DayParams): DaySlots {
  const perStylist = new Map<string, number[]>();
  for (const day of days) {
    perStylist.set(day.stylistId, stylistDaySlots(day, params));
  }
  return { date: params.dateKey, slots: mergeSlots(perStylist) };
}

/** Broj aktivnih termina i hold-ova frizera tog dana (za „bilo koji frizer“). */
export function dayLoad(day: StylistDay, nowMs: number): number {
  return day.busy.filter((b) => (b.kind === "appt" || b.kind === "hold") && isBusyActive(b, nowMs)).length;
}

/**
 * Bira frizera sa najmanje termina tog dana. Kod jednakog broja prednost ima frizer koji je
 * ranije u redosledu salona, pa onda po id-u (deterministično).
 */
export function pickLeastLoaded(
  candidates: StylistDay[],
  nowMs: number,
  order: Map<string, number> = new Map(),
): StylistDay | null {
  if (candidates.length === 0) return null;
  return [...candidates].sort((a, b) => {
    const load = dayLoad(a, nowMs) - dayLoad(b, nowMs);
    if (load !== 0) return load;
    const ord = (order.get(a.stylistId) ?? 0) - (order.get(b.stylistId) ?? 0);
    if (ord !== 0) return ord;
    return a.stylistId.localeCompare(b.stylistId);
  })[0];
}

/** Uklanja istekle hold-ove i intervale koji su odavno prošli (održavanje dokumenta dana). */
export function pruneBusy(busy: BusyInterval[], nowMs: number): BusyInterval[] {
  return busy.filter((b) => isBusyActive(b, nowMs));
}
