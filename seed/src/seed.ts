/**
 * Puni Firebase Emulator Suite demo podacima: nalozi, saloni, usluge, frizeri, recenzije,
 * prošli i budući termini, odsustva i slike u Storage emulatoru.
 * Pokretanje (emulatori moraju da rade): npm run seed
 */
import { readdir, readFile, stat } from "node:fs/promises";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { DateTime } from "luxon";
import geohash from "ngeohash";
import {
  DEMO_CUSTOMER,
  DEMO_OWNER,
  DEMO_STYLIST,
  OTHER_CUSTOMERS,
  SALONS,
  type SeedSalon,
  type SeedService,
  type SeedStylist,
} from "./data.ts";

const PROJECT = process.env.GCLOUD_PROJECT ?? "demo-termin";
if (!PROJECT.startsWith("demo-")) {
  throw new Error(`Seed radi samo sa demo projektom (demo-*), a zadat je "${PROJECT}".`);
}
process.env.GCLOUD_PROJECT = PROJECT;
// Emulatori ne traže prave akreditive; bez ovoga Admin SDK pokušava GCE metadata server.
process.env.METADATA_SERVER_DETECTION ??= "none";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";
process.env.FIREBASE_STORAGE_EMULATOR_HOST ??= "127.0.0.1:9199";

const { initializeApp, getApps } = await import("firebase-admin/app");
const { GeoPoint, Timestamp, FieldValue } = await import("firebase-admin/firestore");

const BUCKET = `${PROJECT}.appspot.com`;
if (getApps().length === 0) initializeApp({ projectId: PROJECT, storageBucket: BUCKET });
// Ista Admin instanca kao u Cloud Functions kodu (koristimo ga za izvedena polja).
const { db, auth, storage } = await import("../../functions/src/common/firebase.ts");
const bucket = storage.bucket(BUCKET);

const ZONE = "Europe/Belgrade";
const IMAGES = join(dirname(fileURLToPath(import.meta.url)), "..", "images");
const now = DateTime.now().setZone(ZONE);
const MINUTE = 60_000;

function log(message: string): void {
  process.stdout.write(`${message}\n`);
}

async function ensureEmulators(): Promise<void> {
  const targets = [
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/`,
    `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/`,
    `http://${process.env.FIREBASE_STORAGE_EMULATOR_HOST}/`,
  ];
  for (const url of targets) {
    try {
      await fetch(url);
    } catch {
      throw new Error(`Emulator nije dostupan na ${url}. Prvo pokreni: npm run emulators`);
    }
  }
}

async function clearAll(): Promise<void> {
  const fs = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: "DELETE" },
  );
  if (!fs.ok) throw new Error(`Brisanje Firestore emulatora nije uspelo: ${fs.status}`);
  const au = await fetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/accounts`, {
    method: "DELETE",
  });
  if (!au.ok) throw new Error(`Brisanje Auth emulatora nije uspelo: ${au.status}`);
}

async function listFiles(dir: string): Promise<string[]> {
  const result: string[] = [];
  for (const entry of await readdir(dir)) {
    const full = join(dir, entry);
    if ((await stat(full)).isDirectory()) result.push(...(await listFiles(full)));
    else if (entry.endsWith(".webp")) result.push(full);
  }
  return result;
}

/** seed/images/<salon>/... → Storage: salons/<salon>/photos/... i salons/<salon>/stylists/<id>/... */
function storagePath(file: string): string {
  const rel = relative(IMAGES, file).split("\\").join("/");
  const [salonId, ...rest] = rel.split("/");
  if (rest[0] === "stylists") return `salons/${salonId}/${rest.join("/")}`;
  return `salons/${salonId}/photos/${rest.join("/")}`;
}

async function uploadImages(): Promise<number> {
  const files = await listFiles(IMAGES);
  await Promise.all(
    files.map(async (file) =>
      bucket.file(storagePath(file)).save(await readFile(file), {
        contentType: "image/webp",
        metadata: { cacheControl: "public, max-age=31536000" },
        resumable: false,
      }),
    ),
  );
  return files.length;
}

function weekdayKey(dt: DateTime): "1" | "2" | "3" | "4" | "5" | "6" | "7" {
  return String(dt.weekday) as "1" | "2" | "3" | "4" | "5" | "6" | "7";
}

function minutes(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

/** Da li frizer radi u lokalnom intervalu [start, start + trajanje) tog dana (bez pauza). */
function works(salon: SeedSalon, stylist: SeedStylist, day: DateTime, startMin: number, durationMin: number): boolean {
  const key = weekdayKey(day);
  const salonRanges = salon.workingHours[key] ?? [];
  const own = stylist.workingHours ? (stylist.workingHours[key] ?? []) : salonRanges;
  const end = startMin + durationMin + salon.rules.bufferMin;
  const inside = (ranges: { start: string; end: string }[]) =>
    ranges.some((r) => startMin >= minutes(r.start) && end <= minutes(r.end));
  const onBreak = (stylist.breaks[key] ?? []).some((b) => startMin < minutes(b.end) && minutes(b.start) < end);
  return inside(salonRanges) && inside(own) && !onBreak;
}

function at(day: DateTime, hhmm: string): DateTime {
  const [hour, minute] = hhmm.split(":").map(Number);
  return day.set({ hour, minute, second: 0, millisecond: 0 });
}

interface PlannedAppointment {
  id: string;
  salon: SeedSalon;
  stylist: SeedStylist;
  services: SeedService[];
  start: DateTime;
  status: "confirmed" | "completed" | "no_show" | "canceled_by_customer" | "canceled_by_salon";
  customer: { uid: string | null; name: string; phone: string | null };
  note?: string;
  source?: "app" | "manual";
  reviewed?: boolean;
}

const planned: PlannedAppointment[] = [];
let counter = 0;
const nextId = (prefix: string) => `${prefix}-${String(++counter).padStart(3, "0")}`;

function busyOverlap(p: PlannedAppointment): boolean {
  const start = p.start.toMillis();
  const end = start + p.services.reduce((s, x) => s + x.durationMin, 0) * MINUTE;
  return planned.some((o) => {
    if (o.stylist.id !== p.stylist.id || o.salon.id !== p.salon.id) return false;
    if (o.status === "canceled_by_customer" || o.status === "canceled_by_salon") return false;
    const os = o.start.toMillis();
    const oe = os + (o.services.reduce((s, x) => s + x.durationMin, 0) + o.salon.rules.bufferMin) * MINUTE;
    return start < oe && os < end + p.salon.rules.bufferMin * MINUTE;
  });
}

function plan(p: Omit<PlannedAppointment, "id">, prefix = "seed"): boolean {
  const item = { ...p, id: nextId(prefix) };
  const duration = p.services.reduce((s, x) => s + x.durationMin, 0);
  if (!works(p.salon, p.stylist, p.start, p.start.hour * 60 + p.start.minute, duration)) return false;
  if (busyOverlap(item)) return false;
  planned.push(item);
  return true;
}

function servicesById(salon: SeedSalon, ids: string[]): SeedService[] {
  return ids.map((id) => {
    const s = salon.services.find((x) => x.id === id);
    if (!s) throw new Error(`Nepoznata usluga ${id} u ${salon.id}`);
    return s;
  });
}

function stylistOf(salon: SeedSalon, id: string): SeedStylist {
  const s = salon.stylists.find((x) => x.id === id);
  if (!s) throw new Error(`Nepoznat frizer ${id}`);
  return s;
}

function planDemoCustomer(): void {
  const lana = SALONS[0];
  const aurora = SALONS[2];
  const vuk = SALONS[1];
  const customer = { uid: DEMO_CUSTOMER.uid, name: DEMO_CUSTOMER.name, phone: DEMO_CUSTOMER.phone };

  // Završena poseta bez recenzije: klijent može odmah da je oceni.
  for (let back = 12; back < 30; back++) {
    const day = now.minus({ days: back });
    if (plan({ salon: lana, stylist: stylistOf(lana, "nina"), services: servicesById(lana, ["zensko-sisanje", "feniranje"]), start: at(day, "11:00"), status: "completed", customer }, "demo")) break;
  }
  // Starija poseta sa recenzijom.
  for (let back = 40; back < 60; back++) {
    const day = now.minus({ days: back });
    if (plan({ salon: aurora, stylist: stylistOf(aurora, "jelena"), services: servicesById(aurora, ["pramenovi"]), start: at(day, "12:00"), status: "completed", customer, reviewed: true }, "demo")) break;
  }
  // Otkazan termin u istoriji.
  for (let back = 20; back < 35; back++) {
    const day = now.minus({ days: back });
    if (plan({ salon: vuk, stylist: stylistOf(vuk, "luka"), services: servicesById(vuk, ["pranje-feniranje"]), start: at(day, "16:00"), status: "canceled_by_customer", customer }, "demo")) break;
  }
  // Predstojeći termin (za par dana), kod Ivane: bojenje.
  for (let ahead = 3; ahead < 14; ahead++) {
    const day = now.plus({ days: ahead });
    if (plan({ salon: lana, stylist: stylistOf(lana, "ivana"), services: servicesById(lana, ["bojenje"]), start: at(day, "13:00"), status: "confirmed", customer, note: "Želim toplu nijansu, kao prošli put." }, "demo")) break;
  }
}

/** Popunjava kalendare: nekoliko termina dnevno kod svakog frizera, prošlih i budućih. */
function planOthers(): void {
  const times = ["09:30", "11:00", "12:30", "15:00", "17:30"];
  let c = 0;
  for (const salon of SALONS) {
    salon.stylists.forEach((stylist, si) => {
      const own = salon.services.filter((s) => s.stylists.includes(stylist.id));
      for (let offset = -28; offset <= 9; offset++) {
        const day = now.plus({ days: offset });
        times.forEach((time, ti) => {
          // Deterministički raspored: otprilike svaki drugi termin je zauzet, ostalo je slobodno.
          if ((offset + si * 2 + ti) % 3 !== 0) return;
          const start = at(day, time);
          if (offset >= 0 && start < now.plus({ minutes: 30 })) return;
          const service = own[(offset + 30 + ti + si) % own.length];
          const customer = OTHER_CUSTOMERS[c++ % OTHER_CUSTOMERS.length];
          const past = start < now;
          const status = past
            ? (offset + ti) % 11 === 0
              ? "no_show"
              : (offset + ti) % 13 === 0
                ? "canceled_by_customer"
                : "completed"
            : "confirmed";
          plan({
            salon,
            stylist,
            services: [service],
            start,
            status,
            customer: { uid: (offset + ti) % 4 === 0 ? null : customer.uid, name: customer.name, phone: customer.phone },
            source: (offset + ti) % 4 === 0 ? "manual" : "app",
          });
        });
      }
    });
  }
}

interface TimeOffPlan {
  salonId: string;
  stylistId: string;
  start: DateTime;
  end: DateTime;
  kind: "vacation" | "block";
  reason: string;
}

function planTimeOff(): TimeOffPlan[] {
  const result: TimeOffPlan[] = [];
  result.push({
    salonId: "atelje-lana",
    stylistId: "nina",
    start: now.plus({ days: 8 }).startOf("day"),
    end: now.plus({ days: 13 }).startOf("day"),
    kind: "vacation",
    reason: "Godišnji odmor",
  });
  for (let ahead = 1; ahead < 7; ahead++) {
    const day = now.plus({ days: ahead });
    const luka = stylistOf(SALONS[1], "luka");
    if (works(SALONS[1], luka, day, 14 * 60, 60)) {
      result.push({ salonId: "kod-vuka", stylistId: "luka", start: at(day, "14:00"), end: at(day, "15:00"), kind: "block", reason: "Edukacija" });
      break;
    }
  }
  return result;
}

function overlapsTimeOff(p: PlannedAppointment, offs: TimeOffPlan[]): boolean {
  const start = p.start.toMillis();
  const end = start + p.services.reduce((s, x) => s + x.durationMin, 0) * MINUTE;
  return offs.some((o) => o.salonId === p.salon.id && o.stylistId === p.stylist.id && start < o.end.toMillis() && o.start.toMillis() < end);
}

async function createAccounts(): Promise<Map<string, string>> {
  const owners = new Map<string, string>();
  const ownerUid = (salon: SeedSalon) =>
    salon.ownerPhone === DEMO_OWNER.phone ? DEMO_OWNER.uid : `owner-${salon.id}`;

  const users: { uid: string; phone: string; name: string; claims: Record<string, unknown> | null; roles: string[]; salonId: string | null; stylistId: string | null }[] = [
    { uid: DEMO_CUSTOMER.uid, phone: DEMO_CUSTOMER.phone, name: DEMO_CUSTOMER.name, claims: null, roles: ["customer"], salonId: null, stylistId: null },
    { uid: DEMO_STYLIST.uid, phone: DEMO_STYLIST.phone, name: DEMO_STYLIST.name, claims: { roles: ["stylist"], salonId: "atelje-lana", stylistId: "stefan" }, roles: ["stylist"], salonId: "atelje-lana", stylistId: "stefan" },
  ];
  for (const salon of SALONS) {
    const uid = ownerUid(salon);
    owners.set(salon.id, uid);
    const ownStylist = salon.stylists.find((s) => s.accountPhone === salon.ownerPhone);
    const roles = ownStylist ? ["owner", "stylist"] : ["owner"];
    users.push({
      uid,
      phone: salon.ownerPhone,
      name: salon.ownerName,
      claims: { roles, salonId: salon.id, ...(ownStylist ? { stylistId: ownStylist.id } : {}) },
      roles,
      salonId: salon.id,
      stylistId: ownStylist?.id ?? null,
    });
  }
  for (const c of OTHER_CUSTOMERS) {
    users.push({ uid: c.uid, phone: c.phone, name: c.name, claims: null, roles: ["customer"], salonId: null, stylistId: null });
  }

  for (const u of users) {
    await auth.createUser({ uid: u.uid, phoneNumber: u.phone, displayName: u.name });
    if (u.claims) await auth.setCustomUserClaims(u.uid, u.claims);
    await db.collection("users").doc(u.uid).set({
      name: u.name,
      phone: u.phone,
      email: null,
      photoPath: null,
      roles: u.roles,
      salonId: u.salonId,
      stylistId: u.stylistId,
      favoriteSalonIds: u.uid === DEMO_CUSTOMER.uid ? ["studio-aurora"] : [],
      favoriteStylistKeys: u.uid === DEMO_CUSTOMER.uid ? ["atelje-lana/ivana"] : [],
      fcmTokens: [],
      language: "sr",
      notificationsEnabled: true,
      createdAt: Timestamp.fromMillis(now.minus({ days: 90 }).toMillis()),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  return owners;
}

function ratingFrom(values: number[]): { avg: number; count: number } {
  if (values.length === 0) return { avg: 0, count: 0 };
  return { avg: Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 100) / 100, count: values.length };
}

async function writeSalons(owners: Map<string, string>, demoReviews: Map<string, { appointmentId: string; stylistId: string; services: string[] }>): Promise<void> {
  for (const salon of SALONS) {
    const ref = db.collection("salons").doc(salon.id);
    const photos = ["gallery-1", "gallery-2", "gallery-3"].map((p) => `salons/${salon.id}/photos/${p}.webp`);
    const demo = demoReviews.get(salon.id);
    const reviewRatings = [...salon.reviews.map((r) => r.rating), ...(demo ? [5] : [])];

    await ref.set({
      name: salon.name,
      slug: salon.slug,
      description: salon.description,
      address: salon.address,
      city: salon.city,
      geo: new GeoPoint(salon.lat, salon.lng),
      geohash: geohash.encode(salon.lat, salon.lng, 9),
      phone: salon.phone,
      photos,
      coverPhoto: `salons/${salon.id}/photos/cover.webp`,
      accentColor: salon.accentColor,
      rating: ratingFrom(reviewRatings),
      priceLevel: salon.priceLevel,
      workingHours: salon.workingHours,
      timezone: ZONE,
      rules: salon.rules,
      ownerUid: owners.get(salon.id),
      published: true,
      categories: [],
      minPrice: null,
      searchKeywords: [],
      nextFreeAt: null,
      createdAt: Timestamp.fromMillis(now.minus({ days: 120 }).toMillis()),
      updatedAt: FieldValue.serverTimestamp(),
    });

    let order = 0;
    for (const s of salon.services) {
      await ref.collection("services").doc(s.id).set({
        category: s.category,
        name: s.name,
        description: s.description,
        durationMin: s.durationMin,
        price: s.price,
        stylistIds: s.stylists,
        active: true,
        order: order++,
      });
    }

    order = 0;
    for (const st of salon.stylists) {
      const ratings = [
        ...salon.reviews.filter((r) => r.stylist === st.id).map((r) => r.rating),
        ...(demo && demo.stylistId === st.id ? [5] : []),
      ];
      const uid =
        st.accountPhone === DEMO_STYLIST.phone
          ? DEMO_STYLIST.uid
          : st.accountPhone === salon.ownerPhone
            ? owners.get(salon.id)
            : null;
      await ref.collection("stylists").doc(st.id).set({
        name: st.name,
        photoPath: `salons/${salon.id}/stylists/${st.id}/portrait.webp`,
        bio: st.bio,
        specialties: st.specialties,
        portfolio: [1, 2].map((n) => `salons/${salon.id}/stylists/${st.id}/portfolio-${n}.webp`),
        uid: uid ?? null,
        workingHours: st.workingHours,
        breaks: st.breaks,
        active: true,
        order: order++,
        color: st.color,
        rating: ratingFrom(ratings),
        nextFreeAt: null,
        createdAt: Timestamp.fromMillis(now.minus({ days: 120 }).toMillis()),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }

    for (const [i, r] of salon.reviews.entries()) {
      const stylist = stylistOf(salon, r.stylist);
      await ref.collection("reviews").doc(`seed-review-${i + 1}`).set({
        uid: null,
        authorName: r.author,
        appointmentId: null,
        stylistId: stylist.id,
        stylistName: stylist.name,
        serviceNames: [],
        rating: r.rating,
        text: r.text,
        reply: r.reply ? { text: r.reply, at: Timestamp.fromMillis(now.minus({ days: r.daysAgo - 1 }).toMillis()) } : null,
        createdAt: Timestamp.fromMillis(now.minus({ days: r.daysAgo }).toMillis()),
      });
    }
    if (demo) {
      const stylist = stylistOf(salon, demo.stylistId);
      await ref.collection("reviews").doc(demo.appointmentId).set({
        uid: DEMO_CUSTOMER.uid,
        authorName: "Jovana M.",
        appointmentId: demo.appointmentId,
        stylistId: stylist.id,
        stylistName: stylist.name,
        serviceNames: demo.services,
        rating: 5,
        text: "Pramenovi su ispali prirodno i baš kako sam zamislila. Hvala, Jelena!",
        reply: null,
        createdAt: Timestamp.fromMillis(now.minus({ days: 39 }).toMillis()),
      });
    }
  }
}

async function writeAppointments(offs: TimeOffPlan[]): Promise<void> {
  const days = new Map<string, { id: string; start: number; end: number; kind: string }[]>();
  const addInterval = (salonId: string, stylistId: string, dateKey: string, interval: { id: string; start: number; end: number; kind: string }) => {
    const key = `salons/${salonId}/stylists/${stylistId}/days/${dateKey}`;
    const list = days.get(key) ?? [];
    list.push(interval);
    days.set(key, list);
  };

  let batch = db.batch();
  let ops = 0;
  const flush = async () => {
    if (ops > 0) await batch.commit();
    batch = db.batch();
    ops = 0;
  };

  for (const p of planned) {
    if (overlapsTimeOff(p, offs)) continue;
    const durationMin = p.services.reduce((s, x) => s + x.durationMin, 0);
    const start = p.start.toMillis();
    const end = start + durationMin * MINUTE;
    const dateKey = p.start.toISODate() as string;
    const canceled = p.status === "canceled_by_customer" || p.status === "canceled_by_salon";
    if (!canceled && end > now.toMillis() - MINUTE) {
      addInterval(p.salon.id, p.stylist.id, dateKey, { id: p.id, start, end, kind: "appt" });
    }
    const past = end < now.toMillis();
    batch.set(db.collection("appointments").doc(p.id), {
      salonId: p.salon.id,
      salonName: p.salon.name,
      salonPhone: p.salon.phone,
      salonAddress: `${p.salon.address}, ${p.salon.city}`,
      stylistId: p.stylist.id,
      stylistName: p.stylist.name,
      customerUid: p.customer.uid,
      customerName: p.customer.name,
      customerPhone: p.customer.phone,
      services: p.services.map((s) => ({ id: s.id, name: s.name, price: s.price, durationMin: s.durationMin })),
      start: Timestamp.fromMillis(start),
      end: Timestamp.fromMillis(end),
      durationMin,
      totalPrice: p.services.reduce((s, x) => s + x.price, 0),
      status: p.status,
      holdExpiresAt: null,
      note: p.note ?? "",
      source: p.source ?? "app",
      externalIds: {},
      anyStylist: false,
      dateKey,
      remindersSent: { h24: past, h2: past, review: past && p.reviewed === true },
      reviewed: p.reviewed === true,
      createdAt: Timestamp.fromMillis(Math.min(start - 3 * 86_400_000, now.toMillis())),
      updatedAt: FieldValue.serverTimestamp(),
      ...(canceled ? { canceledAt: Timestamp.fromMillis(start - 86_400_000), canceledBy: p.customer.uid ?? "salon" } : {}),
    });
    if (++ops >= 400) await flush();
  }

  for (const o of offs) {
    const ref = db.collection("salons").doc(o.salonId).collection("stylists").doc(o.stylistId).collection("timeOff").doc();
    batch.set(ref, {
      start: Timestamp.fromMillis(o.start.toMillis()),
      end: Timestamp.fromMillis(o.end.toMillis()),
      kind: o.kind,
      reason: o.reason,
      createdBy: "seed",
      createdAt: FieldValue.serverTimestamp(),
    });
    for (let d = o.start.startOf("day"); d < o.end; d = d.plus({ days: 1 })) {
      addInterval(o.salonId, o.stylistId, d.toISODate() as string, { id: ref.id, start: o.start.toMillis(), end: o.end.toMillis(), kind: "off" });
    }
    if (++ops >= 400) await flush();
  }

  for (const [path, intervals] of days) {
    batch.set(db.doc(path), { intervals: intervals.sort((a, b) => a.start - b.start), updatedAt: FieldValue.serverTimestamp() });
    if (++ops >= 400) await flush();
  }
  await flush();
}

async function writeInbox(): Promise<void> {
  const { render } = await import("../../functions/src/notifications/templates.ts");
  const upcoming = planned.find((p) => p.customer.uid === DEMO_CUSTOMER.uid && p.status === "confirmed");
  if (!upcoming) return;
  const { title, body } = render("booking_confirmed", "sr", { salonName: upcoming.salon.name, start: upcoming.start.toMillis(), zone: ZONE });
  await db.collection("users").doc(DEMO_CUSTOMER.uid).collection("inbox").doc().set({
    type: "booking_confirmed",
    title,
    body,
    data: { appointmentId: upcoming.id, salonId: upcoming.salon.id },
    read: false,
    createdAt: Timestamp.fromMillis(now.minus({ days: 2 }).toMillis()),
  });
}

async function refreshDerived(): Promise<void> {
  const derived = await import("../../functions/src/salonDerived.ts");
  for (const salon of SALONS) {
    await derived.refreshSalonDerived(salon.id);
    await derived.refreshNextFree(salon.id);
  }
}

async function main(): Promise<void> {
  await ensureEmulators();
  log(`Seed za projekat ${PROJECT} (${now.toFormat("yyyy-MM-dd HH:mm")} ${ZONE})`);
  await clearAll();
  log(`✓ Obrisani stari podaci`);
  log(`✓ Slike: ${await uploadImages()} fajlova u Storage emulatoru`);

  planDemoCustomer();
  planOthers();
  const offs = planTimeOff();
  const owners = await createAccounts();
  log(`✓ Nalozi: ${3 + OTHER_CUSTOMERS.length + 2}`);

  const demoReviews = new Map<string, { appointmentId: string; stylistId: string; services: string[] }>();
  for (const p of planned) {
    if (p.customer.uid === DEMO_CUSTOMER.uid && p.reviewed) {
      demoReviews.set(p.salon.id, { appointmentId: p.id, stylistId: p.stylist.id, services: p.services.map((s) => s.name) });
    }
  }
  await writeSalons(owners, demoReviews);
  log(`✓ Saloni: ${SALONS.map((s) => s.name).join(", ")}`);
  await writeAppointments(offs);
  log(`✓ Termini: ${planned.length} (prošli i budući), odsustva: ${offs.length}`);
  await writeInbox();
  await refreshDerived();
  log(`✓ Izvedena polja (kategorije, cene, prvi slobodan termin)`);
  log("");
  log("Demo nalozi (SMS kod se vidi u Emulator UI → Authentication, ili u aplikaciji preko „Brza demo prijava“):");
  log(`  klijent  ${DEMO_CUSTOMER.phone}  ${DEMO_CUSTOMER.name}`);
  log(`  vlasnik  ${DEMO_OWNER.phone}  ${DEMO_OWNER.name} (Atelje Lana)`);
  log(`  frizer   ${DEMO_STYLIST.phone}  ${DEMO_STYLIST.name} (Atelje Lana)`);
}

await main();
process.exit(0);
