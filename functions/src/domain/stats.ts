import type { AppointmentStatus, BookedService } from "./types";
import { dateKeyOf } from "./time";

export interface StatsAppointment {
  stylistId: string;
  stylistName: string;
  status: AppointmentStatus;
  start: number;
  totalPrice: number;
  services: BookedService[];
}

export interface RankedItem {
  id: string;
  name: string;
  count: number;
}

export interface DayPoint {
  date: string;
  count: number;
  revenue: number;
}

export interface SalonStats {
  total: number;
  confirmed: number;
  completed: number;
  noShow: number;
  canceled: number;
  /** Procena prihoda: završeni termini plus potvrđeni koji tek predstoje. */
  revenueEstimate: number;
  /** Udeo nedolazaka među terminima koji su se završili (završeno + nije došao), 0..1. */
  noShowRate: number;
  topServices: RankedItem[];
  topStylists: RankedItem[];
  series: DayPoint[];
}

function rank(counter: Map<string, RankedItem>, limit: number): RankedItem[] {
  return [...counter.values()]
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, "sr"))
    .slice(0, limit);
}

export function computeStats(appointments: StatsAppointment[], dateKeys: string[], zone: string): SalonStats {
  const services = new Map<string, RankedItem>();
  const stylists = new Map<string, RankedItem>();
  const series = new Map<string, DayPoint>(dateKeys.map((d) => [d, { date: d, count: 0, revenue: 0 }]));
  let confirmed = 0;
  let completed = 0;
  let noShow = 0;
  let canceled = 0;
  let revenue = 0;

  for (const a of appointments) {
    if (a.status === "held") continue;
    const isCanceled = a.status === "canceled_by_customer" || a.status === "canceled_by_salon";
    if (isCanceled) {
      canceled++;
      continue;
    }
    if (a.status === "confirmed") confirmed++;
    if (a.status === "completed") completed++;
    if (a.status === "no_show") noShow++;

    const earns = a.status === "completed" || a.status === "confirmed";
    if (earns) revenue += a.totalPrice;

    const point = series.get(dateKeyOf(a.start, zone));
    if (point) {
      point.count++;
      if (earns) point.revenue += a.totalPrice;
    }

    for (const s of a.services) {
      const item = services.get(s.id) ?? { id: s.id, name: s.name, count: 0 };
      item.count++;
      services.set(s.id, item);
    }
    const st = stylists.get(a.stylistId) ?? { id: a.stylistId, name: a.stylistName, count: 0 };
    st.count++;
    stylists.set(a.stylistId, st);
  }

  const finished = completed + noShow;
  return {
    total: confirmed + completed + noShow,
    confirmed,
    completed,
    noShow,
    canceled,
    revenueEstimate: revenue,
    noShowRate: finished === 0 ? 0 : noShow / finished,
    topServices: rank(services, 5),
    topStylists: rank(stylists, 5),
    series: [...series.values()],
  };
}
