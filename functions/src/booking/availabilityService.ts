import { db } from "../common/firebase";
import { AppError } from "../common/errors";
import { eligibleStylists, loadSalon, loadServices, loadStylists, toBusy, totals } from "../common/repo";
import type { BusyInterval, DaySlots, Salon, Service, Stylist } from "../domain/types";
import { breaksFor, daySlots, effectiveHours, type StylistDay } from "../domain/availability";
import { addDays, dateKeyOf } from "../domain/time";
import { externalBusy } from "../integrations/busy";
import { dayRef } from "./days";

export interface AvailabilityRequest {
  salonId: string;
  serviceIds: string[];
  stylistId: string | null;
  fromDate: string;
  days: number;
  excludeAppointmentId?: string;
}

export interface AvailabilityResult {
  salonId: string;
  durationMin: number;
  totalPrice: number;
  stylistIds: string[];
  days: DaySlots[];
}

/**
 * Spoljni događaj koji se poklapa sa našim terminom (isti frizer, isto vreme) je naš sopstveni
 * upis u spoljni kalendar — ne računa se dvaput.
 */
export function dropOwnEchoes(external: BusyInterval[], own: BusyInterval[]): BusyInterval[] {
  const tolerance = 60_000;
  return external.filter(
    (e) => !own.some((o) => Math.abs(o.start - e.start) < tolerance && Math.abs(o.end - e.end) < tolerance),
  );
}

export function buildStylistDay(
  salon: Salon,
  stylist: Stylist,
  dateKey: string,
  busy: BusyInterval[],
  external: BusyInterval[],
): StylistDay {
  return {
    stylistId: stylist.id,
    hours: effectiveHours(salon.workingHours, stylist.workingHours, dateKey, salon.timezone),
    breaks: breaksFor(stylist.breaks, dateKey, salon.timezone),
    busy: [...busy, ...dropOwnEchoes(external, busy)],
  };
}

export function candidateStylists(stylists: Stylist[], services: Service[], stylistId: string | null): Stylist[] {
  const eligible = eligibleStylists(stylists, services);
  if (stylistId === null) return eligible;
  const chosen = eligible.find((s) => s.id === stylistId);
  if (!chosen) throw new AppError("VALIDATION", "Stylist does not perform all selected services");
  return [chosen];
}

/** Učitava zauzeća frizera za niz dana (jedan getAll za sve dokumente dana). */
export async function loadDays(
  salon: Salon,
  stylists: Stylist[],
  dateKeys: string[],
  nowMs: number,
  excludeAppointmentId?: string,
): Promise<Map<string, StylistDay[]>> {
  const refs = dateKeys.flatMap((d) => stylists.map((s) => dayRef(salon.id, s.id, d)));
  const snaps = refs.length > 0 ? await db.getAll(...refs) : [];
  const external = await externalBusy(salon.id, stylists, salon.timezone, nowMs);
  const byDate = new Map<string, StylistDay[]>();
  let index = 0;
  for (const dateKey of dateKeys) {
    const list: StylistDay[] = [];
    for (const stylist of stylists) {
      const all = toBusy(snaps[index++].data()?.intervals);
      const excluded = all.filter((b) => b.id === excludeAppointmentId);
      const own = all.filter((b) => b.id !== excludeAppointmentId);
      const ext = dropOwnEchoes(external.get(stylist.id) ?? [], excluded);
      list.push(buildStylistDay(salon, stylist, dateKey, own, ext));
    }
    byDate.set(dateKey, list);
  }
  return byDate;
}

export async function computeAvailability(
  request: AvailabilityRequest,
  nowMs = Date.now(),
  options: { allowUnpublished?: boolean } = {},
): Promise<AvailabilityResult> {
  const salon = await loadSalon(request.salonId);
  if (!salon.published && !options.allowUnpublished) {
    throw new AppError("NOT_FOUND", "Salon not found");
  }
  const [services, stylists] = await Promise.all([
    loadServices(request.salonId, request.serviceIds),
    loadStylists(request.salonId),
  ]);
  const candidates = candidateStylists(stylists, services, request.stylistId);
  const { durationMin, totalPrice } = totals(services);

  const today = dateKeyOf(nowMs, salon.timezone);
  const from = request.fromDate < today ? today : request.fromDate;
  const dateKeys = Array.from({ length: request.days }, (_, i) => addDays(from, i, salon.timezone));
  const byDate = await loadDays(salon, candidates, dateKeys, nowMs, request.excludeAppointmentId);

  const days = dateKeys.map((dateKey) =>
    daySlots(byDate.get(dateKey) ?? [], { dateKey, zone: salon.timezone, durationMin, rules: salon.rules, nowMs }),
  );
  return {
    salonId: salon.id,
    durationMin,
    totalPrice,
    stylistIds: candidates.map((c) => c.id),
    days,
  };
}
