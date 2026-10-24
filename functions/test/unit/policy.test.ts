import { describe, expect, it } from "vitest";
import { canCustomerChange, canMarkOutcome, canStaffTransition, cancellationDeadline } from "../../src/domain/policy";
import { computeStats } from "../../src/domain/stats";
import type { SalonRules } from "../../src/domain/types";

const rules: SalonRules = { cancelHours: 24, minNoticeMinutes: 60, horizonDays: 30, slotStepMin: 15, bufferMin: 10 };
const HOUR = 3_600_000;

describe("rok za otkazivanje", () => {
  const start = Date.parse("2026-11-10T10:00:00Z");

  it("rok je X sati pre početka termina", () => {
    expect(cancellationDeadline(start, rules)).toBe(start - 24 * HOUR);
  });

  it("u roku je dozvoljeno, posle roka nije", () => {
    expect(canCustomerChange(start, rules, start - 25 * HOUR)).toBe(true);
    expect(canCustomerChange(start, rules, start - 24 * HOUR)).toBe(true);
    expect(canCustomerChange(start, rules, start - 23 * HOUR)).toBe(false);
    expect(canCustomerChange(start, rules, start + HOUR)).toBe(false);
  });

  it("rok od 0 sati dozvoljava otkazivanje do samog početka", () => {
    expect(canCustomerChange(start, { ...rules, cancelHours: 0 }, start - 1)).toBe(true);
  });
});

describe("prelazi statusa za osoblje", () => {
  it("dozvoljeni prelazi", () => {
    expect(canStaffTransition("confirmed", "completed")).toBe(true);
    expect(canStaffTransition("confirmed", "no_show")).toBe(true);
    expect(canStaffTransition("held", "confirmed")).toBe(true);
    expect(canStaffTransition("no_show", "completed")).toBe(true);
  });

  it("otkazan termin se ne vraća", () => {
    expect(canStaffTransition("canceled_by_customer", "confirmed")).toBe(false);
    expect(canStaffTransition("canceled_by_salon", "completed")).toBe(false);
  });

  it("ishod se označava tek kad termin počne", () => {
    expect(canMarkOutcome(1000, 999)).toBe(false);
    expect(canMarkOutcome(1000, 1000)).toBe(true);
  });
});

describe("statistika", () => {
  const base = { stylistId: "s1", stylistName: "Ana", totalPrice: 1000, services: [{ id: "x", name: "Šišanje", price: 1000, durationMin: 30 }] };
  const day1 = Date.parse("2026-11-10T09:00:00Z");

  it("broji termine, prihod, nedolaske i najtraženije", () => {
    const stats = computeStats(
      [
        { ...base, status: "completed", start: day1 },
        { ...base, status: "completed", start: day1 },
        { ...base, status: "no_show", start: day1 },
        { ...base, status: "confirmed", start: day1, stylistId: "s2", stylistName: "Bojan" },
        { ...base, status: "canceled_by_customer", start: day1 },
        { ...base, status: "held", start: day1 },
      ],
      ["2026-11-10", "2026-11-11"],
      "Europe/Belgrade",
    );
    expect(stats.total).toBe(4);
    expect(stats.completed).toBe(2);
    expect(stats.noShow).toBe(1);
    expect(stats.canceled).toBe(1);
    expect(stats.revenueEstimate).toBe(3000);
    expect(stats.noShowRate).toBeCloseTo(1 / 3);
    expect(stats.topServices[0]).toEqual({ id: "x", name: "Šišanje", count: 4 });
    expect(stats.topStylists.map((s) => s.name)).toEqual(["Ana", "Bojan"]);
    expect(stats.series).toEqual([
      { date: "2026-11-10", count: 4, revenue: 3000 },
      { date: "2026-11-11", count: 0, revenue: 0 },
    ]);
  });

  it("bez termina stopa nedolazaka je 0", () => {
    expect(computeStats([], [], "Europe/Belgrade").noShowRate).toBe(0);
  });
});
