import { describe, expect, it } from "vitest";
import { formatDay, render } from "../../src/notifications/templates";
import { normalize, searchKeywords, deriveFromServices } from "../../src/salonDerived";
import { addRating, displayName, removeRating } from "../../src/reviews";
import { parse } from "../../src/common/validation";
import { AppError } from "../../src/common/errors";
import { periodRange } from "../../src/stats";
import { affectsNextFree, salonNeedsRefresh } from "../../src/triggers";
import { Timestamp } from "firebase-admin/firestore";

describe("datumi na srpskom", () => {
  it("„utorak, 10. novembar“", () => {
    expect(formatDay(Date.parse("2026-11-10T09:00:00Z"), "sr")).toBe("utorak, 10. novembar");
  });

  it("engleski format", () => {
    expect(formatDay(Date.parse("2026-11-10T09:00:00Z"), "en")).toBe("Tuesday, 10 November");
  });

  it("šablon potvrde sadrži salon, dan i vreme po Beogradu", () => {
    const { title, body } = render("booking_confirmed", "sr", { salonName: "Studio Lana", start: Date.parse("2026-11-10T09:00:00Z") });
    expect(title).toBe("Termin je potvrđen");
    expect(body).toBe("Studio Lana te čeka utorak, 10. novembar, 10:00.");
  });
});

describe("pretraga", () => {
  it("uklanja dijakritike", () => {
    expect(normalize("Šišanje, češljanje, Đorđe")).toBe("sisanje, cesljanje, djordje");
  });

  it("pravi prefikse tokena", () => {
    const kw = searchKeywords("Frizerski studio", "Beograd", ["Šišanje"]);
    expect(kw).toContain("fr");
    expect(kw).toContain("frizerski");
    expect(kw).toContain("sis");
    expect(kw).toContain("beograd");
    expect(kw).not.toContain("f");
  });

  it("izvedena polja uzimaju samo aktivne usluge", () => {
    const derived = deriveFromServices("S", "Beograd", [
      { id: "1", category: "haircut", name: "Šišanje", description: "", durationMin: 30, price: 1500, stylistIds: [], active: true, order: 0 },
      { id: "2", category: "coloring", name: "Bojenje", description: "", durationMin: 90, price: 900, stylistIds: [], active: false, order: 1 },
    ]);
    expect(derived.categories).toEqual(["haircut"]);
    expect(derived.minPrice).toBe(1500);
  });
});

describe("ocene", () => {
  it("prosek se preračunava", () => {
    let r = { avg: 0, count: 0 };
    r = addRating(r, 5);
    r = addRating(r, 4);
    expect(r).toEqual({ avg: 4.5, count: 2 });
    expect(removeRating(r, 4)).toEqual({ avg: 5, count: 1 });
    expect(removeRating({ avg: 5, count: 1 }, 5)).toEqual({ avg: 0, count: 0 });
  });

  it("ime autora: ime i inicijal prezimena", () => {
    expect(displayName("Milica Petrović")).toBe("Milica P.");
    expect(displayName("Ana")).toBe("Ana");
    expect(displayName("  ")).toBe("");
  });
});

describe("validacija ulaza (zod)", () => {
  it("odbija neispravan ulaz kodom VALIDATION", () => {
    try {
      parse("holdSlot", { salonId: "s", serviceIds: [], start: 5 });
      expect.unreachable();
    } catch (error) {
      expect(error).toBeInstanceOf(AppError);
      expect((error as AppError).code).toBe("VALIDATION");
    }
  });

  it("popunjava podrazumevane vrednosti", () => {
    const input = parse("holdSlot", { salonId: "s1", serviceIds: ["a"], start: 1_800_000_000_000 });
    expect(input.stylistId).toBeNull();
  });

  it("telefon mora biti u E.164 formatu", () => {
    expect(() => parse("inviteStylist", { salonId: "s", phone: "060123", name: "Ana" })).toThrow(AppError);
    expect(parse("inviteStylist", { salonId: "s", phone: "+381601234567", name: "Ana" }).phone).toBe("+381601234567");
  });

  it("odsustvo ne može da se završi pre početka", () => {
    expect(() => parse("blockTime", { salonId: "s", stylistId: "t", start: 1_800_000_000_000, end: 1_700_000_000_000 })).toThrow(AppError);
  });
});

describe("periodi statistike", () => {
  it("nedelja je ponedeljak–nedelja", () => {
    const r = periodRange("week", "2026-11-12", "Europe/Belgrade");
    expect(r.dateKeys[0]).toBe("2026-11-09");
    expect(r.dateKeys).toHaveLength(7);
  });

  it("mesec ima tačan broj dana", () => {
    expect(periodRange("month", "2026-02-10", "Europe/Belgrade").dateKeys).toHaveLength(28);
  });

  it("dan sa promenom vremena traje 25 sati", () => {
    const r = periodRange("day", "2026-10-25", "Europe/Belgrade");
    expect(r.to - r.from).toBe(25 * 3_600_000);
  });
});

describe("triggeri", () => {
  it("prvi slobodan termin se preračunava samo za relevantne promene", () => {
    const now = Date.parse("2026-11-10T09:00:00Z");
    const start = Timestamp.fromMillis(now + 3_600_000);
    expect(affectsNextFree({ status: "held", start }, { status: "confirmed", start }, now)).toBe(true);
    expect(affectsNextFree({ status: "confirmed", start, note: "a" }, { status: "confirmed", start, note: "b" }, now)).toBe(false);
    const far = Timestamp.fromMillis(now + 40 * 86_400_000);
    expect(affectsNextFree({ status: "held", start: far }, { status: "confirmed", start: far }, now)).toBe(false);
  });

  it("salon se osvežava kad se promeni radno vreme, ne kad server upiše izvedena polja", () => {
    const base = { name: "S", city: "Beograd", geo: { lat: 1 }, geohash: "u", workingHours: {}, rules: {}, published: true };
    expect(salonNeedsRefresh(base, { ...base, nextFreeAt: 5 })).toBe(false);
    expect(salonNeedsRefresh(base, { ...base, workingHours: { "1": [] } })).toBe(true);
  });
});
