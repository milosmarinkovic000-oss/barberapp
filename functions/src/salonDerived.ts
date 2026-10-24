import { Timestamp } from "firebase-admin/firestore";
import geohash from "ngeohash";
import { db } from "./common/firebase";
import { col, toSalon, toService, toStylist, toBusy } from "./common/repo";
import { stylistDaySlots } from "./domain/availability";
import { addDays, dateKeyOf } from "./domain/time";
import type { Service } from "./domain/types";
import { externalBusy } from "./integrations/busy";
import { buildStylistDay } from "./booking/availabilityService";
import { dayRef } from "./booking/days";

/** Mala slova, bez dijakritika (č→c, ć→c, š→s, ž→z, đ→dj). */
export function normalize(text: string): string {
  return text
    .toLowerCase()
    .replace(/đ/g, "dj")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "");
}

/**
 * Ključne reči za pretragu: tokeni naziva salona, grada i usluga, plus prefiksi (od 2 slova),
 * da bi pretraga „fri“ našla „Frizerski studio“.
 */
export function searchKeywords(name: string, city: string, serviceNames: string[]): string[] {
  const tokens = new Set<string>();
  for (const text of [name, city, ...serviceNames]) {
    for (const token of normalize(text).split(/[^a-z0-9]+/)) {
      if (token.length < 2) continue;
      for (let i = 2; i <= Math.min(token.length, 15); i++) tokens.add(token.slice(0, i));
    }
  }
  return [...tokens].sort().slice(0, 400);
}

/** Izvedena polja salona iz usluga: kategorije, najniža cena i ključne reči. */
export function deriveFromServices(name: string, city: string, services: Service[]) {
  const active = services.filter((s) => s.active);
  return {
    categories: [...new Set(active.map((s) => s.category))].sort(),
    minPrice: active.length > 0 ? Math.min(...active.map((s) => s.price)) : null,
    searchKeywords: searchKeywords(name, city, active.map((s) => s.name)),
  };
}

export async function refreshSalonDerived(salonId: string): Promise<void> {
  const salonRef = col.salons().doc(salonId);
  const [salonSnap, servicesSnap] = await Promise.all([salonRef.get(), col.services(salonId).get()]);
  if (!salonSnap.exists) return;
  const data = salonSnap.data() ?? {};
  const derived = deriveFromServices(
    (data.name as string) ?? "",
    (data.city as string) ?? "",
    servicesSnap.docs.map(toService),
  );
  const updates: Record<string, unknown> = {};
  if (JSON.stringify(data.categories ?? []) !== JSON.stringify(derived.categories)) updates.categories = derived.categories;
  if ((data.minPrice ?? null) !== derived.minPrice) updates.minPrice = derived.minPrice;
  if (JSON.stringify(data.searchKeywords ?? []) !== JSON.stringify(derived.searchKeywords)) {
    updates.searchKeywords = derived.searchKeywords;
  }
  const geo = data.geo as FirebaseFirestore.GeoPoint | undefined;
  if (geo) {
    const hash = geohash.encode(geo.latitude, geo.longitude, 9);
    if (hash !== data.geohash) updates.geohash = hash;
  }
  if (Object.keys(updates).length > 0) await salonRef.update(updates);
}

const SCAN_DAYS = 14;

/**
 * Prvi slobodan termin salona i svakog frizera (za kartice, „Slobodno danas“ i profil frizera).
 * Gleda najkraću uslugu koju frizer radi, najviše 14 dana unapred.
 */
export async function refreshNextFree(salonId: string, nowMs = Date.now()): Promise<number | null> {
  const salonSnap = await col.salons().doc(salonId).get();
  if (!salonSnap.exists) return null;
  const salon = toSalon(salonSnap);
  const [servicesSnap, stylistsSnap] = await Promise.all([col.services(salonId).get(), col.stylists(salonId).get()]);
  const services = servicesSnap.docs.map(toService).filter((s) => s.active);
  const stylists = stylistsSnap.docs.map(toStylist).filter((s) => s.active);
  const zone = salon.timezone;
  const today = dateKeyOf(nowMs, zone);
  const external = await externalBusy(salonId, stylists, zone, nowMs);

  const results = new Map<string, number | null>();
  for (const stylist of stylists) {
    const durations = services.filter((s) => s.stylistIds.includes(stylist.id)).map((s) => s.durationMin);
    if (durations.length === 0) {
      results.set(stylist.id, null);
      continue;
    }
    const durationMin = Math.min(...durations);
    const dateKeys = Array.from({ length: Math.min(SCAN_DAYS, salon.rules.horizonDays) }, (_, i) => addDays(today, i, zone));
    const snaps = await db.getAll(...dateKeys.map((d) => dayRef(salonId, stylist.id, d)));
    let found: number | null = null;
    for (let i = 0; i < dateKeys.length && found === null; i++) {
      const day = buildStylistDay(salon, stylist, dateKeys[i], toBusy(snaps[i].data()?.intervals), external.get(stylist.id) ?? []);
      const slots = stylistDaySlots(day, { dateKey: dateKeys[i], zone, durationMin, rules: salon.rules, nowMs });
      found = slots[0] ?? null;
    }
    results.set(stylist.id, found);
  }

  const values = [...results.values()].filter((v): v is number => v !== null);
  const salonNext = values.length > 0 ? Math.min(...values) : null;
  const batch = db.batch();
  const current = salonSnap.data()?.nextFreeAt as Timestamp | null | undefined;
  if ((current?.toMillis() ?? null) !== salonNext) {
    batch.update(salonSnap.ref, { nextFreeAt: salonNext === null ? null : Timestamp.fromMillis(salonNext) });
  }
  for (const doc of stylistsSnap.docs) {
    if (!results.has(doc.id)) continue;
    const value = results.get(doc.id) ?? null;
    const existing = doc.data().nextFreeAt as Timestamp | null | undefined;
    if ((existing?.toMillis() ?? null) !== value) {
      batch.update(doc.ref, { nextFreeAt: value === null ? null : Timestamp.fromMillis(value) });
    }
  }
  await batch.commit();
  return salonNext;
}

