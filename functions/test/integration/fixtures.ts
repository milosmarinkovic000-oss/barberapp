import { DateTime } from "luxon";
import { GeoPoint } from "firebase-admin/firestore";
import { auth, db } from "../../src/common/firebase";
import type { Caller } from "../../src/common/caller";

export const ZONE = "Europe/Belgrade";
export const PROJECT = process.env.GCLOUD_PROJECT ?? "demo-termin";

export function requireEmulator(): void {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Integration tests need the Firestore emulator (run `npm test` from the repository root).");
  }
}

export async function clearFirestore(): Promise<void> {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const response = await fetch(`http://${host}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: "DELETE" });
  if (!response.ok) throw new Error(`Could not clear Firestore emulator: ${response.status}`);
}

export async function clearAuth(): Promise<void> {
  const host = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  if (!host) return;
  await fetch(`http://${host}/emulator/v1/projects/${PROJECT}/accounts`, { method: "DELETE" });
}

/** Lokalno vreme u Beogradu za dan od danas (+offset) → UTC ms. */
export function localAt(dayOffset: number, hhmm: string): number {
  const [hour, minute] = hhmm.split(":").map(Number);
  return DateTime.now().setZone(ZONE).plus({ days: dayOffset }).set({ hour, minute, second: 0, millisecond: 0 }).toMillis();
}

export function dateKey(dayOffset: number): string {
  return DateTime.now().setZone(ZONE).plus({ days: dayOffset }).toISODate() as string;
}

const ALL_DAYS = Object.fromEntries(["1", "2", "3", "4", "5", "6", "7"].map((d) => [d, [{ start: "08:00", end: "20:00" }]]));

export interface SalonFixture {
  salonId: string;
  ownerUid: string;
  stylistA: string;
  stylistB: string;
  stylistAUid: string;
  haircut: string;
  beard: string;
  coloring: string;
}

export async function createUser(uid: string, phone: string, name: string): Promise<Caller> {
  await auth.createUser({ uid, phoneNumber: phone, displayName: name }).catch(() => undefined);
  await db.collection("users").doc(uid).set({
    name,
    phone,
    roles: ["customer"],
    language: "sr",
    fcmTokens: [],
    favoriteSalonIds: [],
    favoriteStylistKeys: [],
    notificationsEnabled: true,
  });
  return { uid, roles: [], salonId: null, stylistId: null, phone, email: null, displayName: name };
}

export function staffCaller(uid: string, salonId: string, roles: string[], stylistId: string | null = null): Caller {
  return { uid, roles, salonId, stylistId, phone: null, email: null, displayName: "Osoblje" };
}

export async function createSalon(id = "salon-test", rules: Partial<Record<string, number>> = {}): Promise<SalonFixture> {
  const salonRef = db.collection("salons").doc(id);
  await salonRef.set({
    name: "Test salon",
    slug: "test-salon",
    description: "",
    address: "Knez Mihailova 1",
    city: "Beograd",
    geo: new GeoPoint(44.8176, 20.4569),
    geohash: "srywc",
    phone: "+381111234567",
    photos: [],
    coverPhoto: null,
    accentColor: "#8C6A3F",
    rating: { avg: 0, count: 0 },
    priceLevel: 2,
    workingHours: ALL_DAYS,
    timezone: ZONE,
    rules: { cancelHours: 12, minNoticeMinutes: 0, horizonDays: 30, slotStepMin: 15, bufferMin: 0, ...rules },
    ownerUid: "owner-1",
    published: true,
  });
  await salonRef.collection("stylists").doc("ana").set({
    name: "Ana",
    uid: "stylist-ana",
    active: true,
    workingHours: null,
    breaks: {},
    order: 0,
    rating: { avg: 0, count: 0 },
  });
  await salonRef.collection("stylists").doc("bojan").set({
    name: "Bojan",
    uid: null,
    active: true,
    workingHours: null,
    breaks: {},
    order: 1,
    rating: { avg: 0, count: 0 },
  });
  await salonRef.collection("services").doc("haircut").set({
    category: "haircut", name: "Šišanje", description: "", durationMin: 30, price: 1500, stylistIds: ["ana", "bojan"], active: true, order: 0,
  });
  await salonRef.collection("services").doc("beard").set({
    category: "beard", name: "Brada", description: "", durationMin: 30, price: 800, stylistIds: ["ana", "bojan"], active: true, order: 1,
  });
  await salonRef.collection("services").doc("coloring").set({
    category: "coloring", name: "Bojenje", description: "", durationMin: 90, price: 4500, stylistIds: ["ana"], active: true, order: 2,
  });
  return {
    salonId: id,
    ownerUid: "owner-1",
    stylistA: "ana",
    stylistB: "bojan",
    stylistAUid: "stylist-ana",
    haircut: "haircut",
    beard: "beard",
    coloring: "coloring",
  };
}

export function errorCode(error: unknown): string | undefined {
  return (error as { code?: string }).code;
}
