import { describe, expect, it } from "vitest";
import { DateTime } from "luxon";
import {
  daySlots,
  effectiveHours,
  fitsDay,
  isBusyActive,
  lastBookableDate,
  mergeSlots,
  pickLeastLoaded,
  stylistDaySlots,
  withinBookingWindow,
  type DayParams,
  type StylistDay,
} from "../../src/domain/availability";
import type { BusyInterval, SalonRules, WeeklyHours } from "../../src/domain/types";
import { dateKeyOf, localToUtc } from "../../src/domain/time";

const ZONE = "Europe/Belgrade";
const MIN = 60_000;

const rules: SalonRules = { cancelHours: 12, minNoticeMinutes: 0, horizonDays: 30, slotStepMin: 15, bufferMin: 0 };

/** Lokalno vreme u Beogradu → UTC ms. */
function at(dateKey: string, hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return DateTime.fromObject(
    { year: +dateKey.slice(0, 4), month: +dateKey.slice(5, 7), day: +dateKey.slice(8, 10), hour: h, minute: m },
    { zone: ZONE },
  ).toMillis();
}

function local(ms: number): string {
  return DateTime.fromMillis(ms, { zone: ZONE }).toFormat("HH:mm");
}

const DATE = "2026-11-10"; // utorak
const NOW = at("2026-11-09", "08:00");

function day(overrides: Partial<StylistDay> = {}): StylistDay {
  return {
    stylistId: "s1",
    hours: [{ start: "09:00", end: "12:00" }],
    breaks: [],
    busy: [],
    ...overrides,
  };
}

function params(overrides: Partial<DayParams> = {}): DayParams {
  return { dateKey: DATE, zone: ZONE, durationMin: 60, rules, nowMs: NOW, ...overrides };
}

describe("osnovni slotovi", () => {
  it("pravi slotove na svakih 15 minuta tako da ceo termin staje u radno vreme", () => {
    const slots = stylistDaySlots(day(), params());
    expect(slots.map(local)).toEqual([
      "09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30", "10:45", "11:00",
    ]);
  });

  it("ceo interval trajanje + pauza mora da stane pre zatvaranja", () => {
    const slots = stylistDaySlots(day(), params({ rules: { ...rules, bufferMin: 15 } }));
    expect(slots.map(local).at(-1)).toBe("10:45");
  });

  it("poštuje korak slotova iz pravila salona", () => {
    const slots = stylistDaySlots(day(), params({ rules: { ...rules, slotStepMin: 30 } }));
    expect(slots.map(local)).toEqual(["09:00", "09:30", "10:00", "10:30", "11:00"]);
  });

  it("dan kada frizer ne radi nema slotova", () => {
    expect(stylistDaySlots(day({ hours: [] }), params())).toEqual([]);
  });
});

describe("preklapanja i pauze između termina", () => {
  const appt: BusyInterval = { id: "a1", start: at(DATE, "10:00"), end: at(DATE, "10:30"), kind: "appt" };

  it("slot se ne preklapa sa postojećim terminom", () => {
    const slots = stylistDaySlots(day({ busy: [appt] }), params({ durationMin: 30 })).map(local);
    expect(slots).not.toContain("10:00");
    expect(slots).not.toContain("09:45");
    expect(slots).toContain("09:30");
    expect(slots).toContain("10:30");
  });

  it("pauza između termina važi i pre i posle postojećeg termina", () => {
    const slots = stylistDaySlots(day({ busy: [appt] }), params({ durationMin: 30, rules: { ...rules, bufferMin: 15 } })).map(local);
    // pre termina: 09:15 + 30 min + 15 min pauze = 10:00 → staje
    expect(slots).toContain("09:15");
    expect(slots).not.toContain("09:30");
    // posle termina: 10:30 + 15 min pauze = 10:45
    expect(slots).not.toContain("10:30");
    expect(slots).toContain("10:45");
  });

  it("aktivan hold zauzima vreme, istekao hold se odmah ignoriše", () => {
    const active: BusyInterval = { id: "h1", start: at(DATE, "09:00"), end: at(DATE, "10:00"), kind: "hold", expiresAt: NOW + 5 * MIN };
    const expired: BusyInterval = { ...active, id: "h2", expiresAt: NOW - 1 };
    expect(isBusyActive(active, NOW)).toBe(true);
    expect(isBusyActive(expired, NOW)).toBe(false);
    expect(stylistDaySlots(day({ busy: [active] }), params()).map(local)[0]).toBe("10:00");
    expect(stylistDaySlots(day({ busy: [expired] }), params()).map(local)[0]).toBe("09:00");
  });

  it("pauza frizera (npr. ručak) uklanja slotove", () => {
    const slots = stylistDaySlots(
      day({ hours: [{ start: "09:00", end: "15:00" }], breaks: [{ start: "12:00", end: "13:00" }] }),
      params(),
    ).map(local);
    expect(slots).toContain("11:00");
    expect(slots).not.toContain("11:15");
    expect(slots).not.toContain("12:30");
    expect(slots).toContain("13:00");
  });

  it("odsustvo i blokada vremena uklanjaju sve slotove koje pokrivaju", () => {
    const off: BusyInterval = { id: "t1", start: at(DATE, "00:00"), end: at("2026-11-11", "00:00"), kind: "off" };
    expect(stylistDaySlots(day({ busy: [off] }), params())).toEqual([]);
    const block: BusyInterval = { id: "b1", start: at(DATE, "09:30"), end: at(DATE, "10:30"), kind: "off" };
    const slots = stylistDaySlots(day({ busy: [block] }), params({ durationMin: 30 })).map(local);
    expect(slots).toEqual(["09:00", "10:30", "10:45", "11:00", "11:15", "11:30"]);
  });

  it("zauzetost iz spoljnog kalendara se poštuje", () => {
    const ext: BusyInterval = { id: "e1", start: at(DATE, "09:00"), end: at(DATE, "11:00"), kind: "external" };
    expect(stylistDaySlots(day({ busy: [ext] }), params()).map(local)).toEqual(["11:00"]);
  });

  it("fitsDay isključuje sopstveni termin pri promeni termina", () => {
    const own: BusyInterval = { id: "mine", start: at(DATE, "09:00"), end: at(DATE, "10:00"), kind: "appt" };
    expect(fitsDay(at(DATE, "09:30"), day({ busy: [own] }), params())).toBe(false);
    expect(fitsDay(at(DATE, "09:30"), day({ busy: [own] }), params(), { excludeId: "mine" })).toBe(true);
  });
});

describe("najkraće vreme do termina i horizont", () => {
  it("slot pre najkraćeg vremena do termina nije ponuđen", () => {
    const now = at(DATE, "09:20");
    const slots = stylistDaySlots(day(), params({ nowMs: now, rules: { ...rules, minNoticeMinutes: 60 } })).map(local);
    expect(slots[0]).toBe("10:30");
  });

  it("dani posle horizonta nemaju slotove", () => {
    const short = { ...rules, horizonDays: 2 };
    expect(lastBookableDate(NOW, short, ZONE)).toBe("2026-11-10");
    expect(stylistDaySlots(day(), params({ rules: short }))).not.toEqual([]);
    expect(stylistDaySlots(day(), params({ dateKey: "2026-11-11", rules: short }))).toEqual([]);
    expect(withinBookingWindow(at("2026-11-11", "10:00"), short, NOW, ZONE)).toBe(false);
  });

  it("prošli dani nemaju slotove", () => {
    expect(stylistDaySlots(day(), params({ dateKey: "2026-11-08" }))).toEqual([]);
  });
});

describe("„Bilo koji frizer“", () => {
  const a = day({ stylistId: "ana", hours: [{ start: "09:00", end: "10:00" }] });
  const b = day({ stylistId: "bojan", hours: [{ start: "09:30", end: "10:30" }] });

  it("unija slotova svih frizera, sa listom slobodnih frizera po slotu", () => {
    const result = daySlots([a, b], params({ durationMin: 30 }));
    expect(result.slots.map((s) => [local(s.start), s.stylistIds])).toEqual([
      ["09:00", ["ana"]],
      ["09:15", ["ana"]],
      ["09:30", ["ana", "bojan"]],
      ["09:45", ["bojan"]],
      ["10:00", ["bojan"]],
    ]);
  });

  it("mergeSlots spaja iste početke", () => {
    expect(mergeSlots(new Map([["x", [1, 2]], ["y", [2, 3]]]))).toEqual([
      { start: 1, stylistIds: ["x"] },
      { start: 2, stylistIds: ["x", "y"] },
      { start: 3, stylistIds: ["y"] },
    ]);
  });

  it("dodeljuje se frizer sa najmanje termina tog dana", () => {
    const busyA = day({ stylistId: "ana", busy: [
      { id: "1", start: at(DATE, "11:00"), end: at(DATE, "11:30"), kind: "appt" },
      { id: "2", start: at(DATE, "11:30"), end: at(DATE, "12:00"), kind: "appt" },
    ] });
    const busyB = day({ stylistId: "bojan", busy: [{ id: "3", start: at(DATE, "11:00"), end: at(DATE, "11:30"), kind: "appt" }] });
    expect(pickLeastLoaded([busyA, busyB], NOW)?.stylistId).toBe("bojan");
  });

  it("istekli hold se ne broji u opterećenje; kod jednakosti odlučuje redosled", () => {
    const withExpired = day({ stylistId: "ana", busy: [{ id: "h", start: 1, end: 2, kind: "hold", expiresAt: NOW - 1 }] });
    const plain = day({ stylistId: "bojan" });
    expect(pickLeastLoaded([withExpired, plain], NOW, new Map([["ana", 0], ["bojan", 1]]))?.stylistId).toBe("ana");
    expect(pickLeastLoaded([], NOW)).toBeNull();
  });
});

describe("radno vreme salona i frizera", () => {
  const salon: WeeklyHours = { "2": [{ start: "09:00", end: "20:00" }], "7": [] };

  it("frizer bez rasporeda radi po radnom vremenu salona", () => {
    expect(effectiveHours(salon, null, DATE, ZONE)).toEqual([{ start: "09:00", end: "20:00" }]);
  });

  it("raspored frizera se preseca sa radnim vremenom salona", () => {
    const own: WeeklyHours = { "2": [{ start: "07:00", end: "13:00" }, { start: "16:00", end: "22:00" }] };
    expect(effectiveHours(salon, own, DATE, ZONE)).toEqual([
      { start: "09:00", end: "13:00" },
      { start: "16:00", end: "20:00" },
    ]);
  });

  it("nedelja zatvoreno", () => {
    expect(effectiveHours(salon, null, "2026-11-15", ZONE)).toEqual([]);
  });
});

describe("dani promene vremena (Europe/Belgrade)", () => {
  it("prelazak na letnje vreme (29. mart 2026): 02:00–03:00 ne postoji", () => {
    expect(localToUtc("2026-03-29", 2 * 60 + 30, ZONE)).toBeNull();
    const d: StylistDay = { stylistId: "s", hours: [{ start: "01:00", end: "05:00" }], breaks: [], busy: [] };
    const slots = stylistDaySlots(d, {
      dateKey: "2026-03-29",
      zone: ZONE,
      durationMin: 60,
      rules: { ...rules, slotStepMin: 60 },
      nowMs: Date.parse("2026-03-28T08:00:00Z"),
    });
    expect(slots.map((s) => new Date(s).toISOString())).toEqual([
      "2026-03-29T00:00:00.000Z", // 01:00 CET
      "2026-03-29T01:00:00.000Z", // 03:00 CEST
      "2026-03-29T02:00:00.000Z", // 04:00 CEST
    ]);
  });

  it("9:00 pre i posle prelaska na letnje vreme pomera UTC za jedan sat", () => {
    expect(new Date(localToUtc("2026-03-28", 9 * 60, ZONE)!).toISOString()).toBe("2026-03-28T08:00:00.000Z");
    expect(new Date(localToUtc("2026-03-29", 9 * 60, ZONE)!).toISOString()).toBe("2026-03-29T07:00:00.000Z");
  });

  it("povratak na zimsko vreme (25. oktobar 2026): dan ima 25 sati i slotovi su ispravni", () => {
    const d: StylistDay = { stylistId: "s", hours: [{ start: "09:00", end: "11:00" }], breaks: [], busy: [] };
    const slots = stylistDaySlots(d, {
      dateKey: "2026-10-25",
      zone: ZONE,
      durationMin: 60,
      rules: { ...rules, slotStepMin: 60 },
      nowMs: Date.parse("2026-10-24T08:00:00Z"),
    });
    expect(slots.map((s) => new Date(s).toISOString())).toEqual(["2026-10-25T08:00:00.000Z", "2026-10-25T09:00:00.000Z"]);
  });

  it("datum dana se računa po Beogradu, ne po UTC", () => {
    expect(dateKeyOf(Date.parse("2026-10-24T22:30:00Z"), ZONE)).toBe("2026-10-25");
    expect(dateKeyOf(Date.parse("2026-12-31T23:30:00Z"), ZONE)).toBe("2027-01-01");
  });
});
