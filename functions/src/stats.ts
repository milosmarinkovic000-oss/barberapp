import { DateTime } from "luxon";
import { Timestamp } from "firebase-admin/firestore";
import type { Caller } from "./common/caller";
import { assertOwner } from "./common/caller";
import type { Input } from "./common/validation";
import { col, loadSalon, toAppointment } from "./common/repo";
import { computeStats, type SalonStats } from "./domain/stats";

/** Opseg perioda u zoni salona: dan, ISO nedelja (pon–ned) ili kalendarski mesec. */
export function periodRange(period: "day" | "week" | "month", dateKey: string, zone: string): { from: number; to: number; dateKeys: string[] } {
  const date = DateTime.fromISO(dateKey, { zone });
  const start = period === "day" ? date.startOf("day") : period === "week" ? date.startOf("week") : date.startOf("month");
  const end = period === "day" ? start.plus({ days: 1 }) : period === "week" ? start.plus({ weeks: 1 }) : start.plus({ months: 1 });
  const dateKeys: string[] = [];
  for (let d = start; d < end; d = d.plus({ days: 1 })) dateKeys.push(d.toISODate() as string);
  return { from: start.toMillis(), to: end.toMillis(), dateKeys };
}

export async function getSalonStats(caller: Caller, input: Input<"getSalonStats">): Promise<SalonStats & { from: number; to: number }> {
  assertOwner(caller, input.salonId);
  const salon = await loadSalon(input.salonId);
  const range = periodRange(input.period, input.date, salon.timezone);
  const snaps = await col
    .appointments()
    .where("salonId", "==", input.salonId)
    .where("start", ">=", Timestamp.fromMillis(range.from))
    .where("start", "<", Timestamp.fromMillis(range.to))
    .get();
  const stats = computeStats(snaps.docs.map(toAppointment), range.dateKeys, salon.timezone);
  return { ...stats, from: range.from, to: range.to };
}
