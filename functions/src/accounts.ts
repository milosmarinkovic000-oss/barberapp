import { FieldValue, GeoPoint, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import geohash from "ngeohash";
import { auth, db, storage } from "./common/firebase";
import { AppError, notAllowed, notFound } from "./common/errors";
import type { Caller } from "./common/caller";
import { assertOwner } from "./common/caller";
import type { Input } from "./common/validation";
import { col, toAppointment } from "./common/repo";
import { DEFAULT_RULES } from "./domain/types";
import { DEFAULT_ZONE } from "./domain/time";
import { cancelAppointment, releaseHold } from "./booking/manage";
import { notifyUser } from "./notifications/notify";
import { searchKeywords } from "./salonDerived";

export interface Claims {
  roles: string[];
  salonId: string | null;
  stylistId: string | null;
}

/** Paleta boja za kolone frizera u kalendaru. */
const STYLIST_COLORS = ["#8C6D46", "#4F6F64", "#7A4E5A", "#4A5F86", "#8A5A3C", "#5E6B3A"];

/** Akcentna boja salona (8 ponuđenih nijansi, sve proverenog kontrasta prema beloj). */
export const SALON_ACCENTS = ["#8C6A3F", "#7A1F3D", "#0F6B5C", "#1E3A8A", "#B4432B", "#5B4B8A", "#3F5E2A", "#6B5B4E"];

export async function setClaims(uid: string, claims: Claims): Promise<void> {
  await auth.setCustomUserClaims(uid, {
    roles: claims.roles,
    ...(claims.salonId ? { salonId: claims.salonId } : {}),
    ...(claims.stylistId ? { stylistId: claims.stylistId } : {}),
  });
}

function claimsOf(record: { customClaims?: Record<string, unknown> }): Claims {
  const c = record.customClaims ?? {};
  return {
    roles: Array.isArray(c.roles) ? (c.roles as string[]) : [],
    salonId: typeof c.salonId === "string" ? c.salonId : null,
    stylistId: typeof c.stylistId === "string" ? c.stylistId : null,
  };
}

async function linkStylist(uid: string, salonId: string, stylistId: string, current: Claims): Promise<Claims> {
  if (current.salonId && current.salonId !== salonId) {
    throw notAllowed("User already belongs to another salon");
  }
  const claims: Claims = {
    roles: [...new Set([...current.roles, "stylist"])],
    salonId,
    stylistId,
  };
  await setClaims(uid, claims);
  const batch = db.batch();
  batch.update(col.stylists(salonId).doc(stylistId), { uid, formerUid: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
  batch.set(
    col.users().doc(uid),
    { roles: claims.roles, salonId, stylistId, updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
  await batch.commit();
  return claims;
}

/**
 * Poziva se posle svake prijave: pravi profil ako ne postoji, prihvata pozive za frizera
 * poslate na broj telefona korisnika i vraća trenutne uloge.
 */
export async function syncAccount(caller: Caller, input: Input<"syncAccount">): Promise<Claims & { claimsChanged: boolean; isNew: boolean }> {
  const userRef = col.users().doc(caller.uid);
  const snap = await userRef.get();
  const isNew = !snap.exists;
  if (isNew) {
    await userRef.set({
      name: input.name ?? caller.displayName ?? "",
      phone: caller.phone,
      email: caller.email,
      photoPath: null,
      roles: ["customer"],
      salonId: null,
      stylistId: null,
      favoriteSalonIds: [],
      favoriteStylistKeys: [],
      fcmTokens: [],
      language: input.language ?? "sr",
      notificationsEnabled: true,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  } else {
    const updates: Record<string, unknown> = {};
    if (input.name !== undefined && input.name.length > 0) updates.name = input.name;
    if (input.language) updates.language = input.language;
    if (caller.phone && snap.data()?.phone !== caller.phone) updates.phone = caller.phone;
    if (Object.keys(updates).length > 0) await userRef.update({ ...updates, updatedAt: FieldValue.serverTimestamp() });
  }

  const record = await auth.getUser(caller.uid);
  let claims = claimsOf(record);
  let changed = false;
  if (caller.phone) {
    const invite = await col.invites().doc(caller.phone).get();
    const data = invite.data();
    if (invite.exists && data && (!claims.salonId || claims.salonId === data.salonId)) {
      claims = await linkStylist(caller.uid, data.salonId as string, data.stylistId as string, claims);
      await invite.ref.delete();
      changed = true;
      const salon = await col.salons().doc(data.salonId as string).get();
      await notifyUser(caller.uid, "stylist_invited", { salonName: (salon.data()?.name as string) ?? "" }, { salonId: salon.id });
    }
  }
  return { ...claims, claimsChanged: changed, isNew };
}

export async function inviteStylist(caller: Caller, input: Input<"inviteStylist">): Promise<{ stylistId: string; linked: boolean }> {
  assertOwner(caller, input.salonId);
  const stylists = await col.stylists(input.salonId).get();
  const pending = await col.invites().doc(input.phone).get();
  if (pending.exists) throw new AppError("POLICY_VIOLATION", "This phone already has a pending invite", { reason: "ALREADY_INVITED" });

  const ref = col.stylists(input.salonId).doc();
  await ref.set({
    name: input.name,
    photoPath: null,
    bio: "",
    specialties: [],
    portfolio: [],
    uid: null,
    workingHours: null,
    breaks: {},
    active: true,
    order: stylists.size,
    color: STYLIST_COLORS[stylists.size % STYLIST_COLORS.length],
    rating: { avg: 0, count: 0 },
    nextFreeAt: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  // Novi frizer podrazumevano radi sve aktivne usluge; vlasnik to menja u izmeni usluge.
  const services = await col.services(input.salonId).where("active", "==", true).get();
  if (!services.empty) {
    const batch = db.batch();
    services.docs.forEach((d) => batch.update(d.ref, { stylistIds: FieldValue.arrayUnion(ref.id) }));
    await batch.commit();
  }

  const existingUid = await auth
    .getUserByPhoneNumber(input.phone)
    .then((u) => u.uid)
    .catch(() => null);
  if (existingUid) {
    const record = await auth.getUser(existingUid);
    const current = claimsOf(record);
    if (current.salonId && current.salonId !== input.salonId) {
      await ref.delete();
      throw notAllowed("This person already works in another salon");
    }
    await linkStylist(existingUid, input.salonId, ref.id, current);
    const salon = await col.salons().doc(input.salonId).get();
    await notifyUser(existingUid, "stylist_invited", { salonName: (salon.data()?.name as string) ?? "" }, { salonId: input.salonId });
    return { stylistId: ref.id, linked: true };
  }
  await col.invites().doc(input.phone).set({
    salonId: input.salonId,
    stylistId: ref.id,
    name: input.name,
    invitedBy: caller.uid,
    createdAt: FieldValue.serverTimestamp(),
  });
  return { stylistId: ref.id, linked: false };
}

/**
 * Vlasnik uključuje ili isključuje pristup frizera: „customer“ uklanja ulogu frizera (profil
 * frizera ostaje u salonu, neaktivan), „stylist“ vraća pristup istoj osobi.
 */
export async function setUserRole(caller: Caller, input: Input<"setUserRole">): Promise<Claims> {
  assertOwner(caller, input.salonId);
  const ref = col.stylists(input.salonId).doc(input.stylistId);
  const snap = await ref.get();
  if (!snap.exists) throw notFound("Stylist");
  const data = snap.data() ?? {};
  const uid = (input.role === "customer" ? data.uid : data.formerUid) as string | undefined;
  if (!uid) throw new AppError("POLICY_VIOLATION", "No account is linked to this stylist", { reason: "NO_ACCOUNT" });
  const record = await auth.getUser(uid);
  const current = claimsOf(record);
  if (input.role === "customer") {
    const roles = current.roles.filter((r) => r !== "stylist");
    const claims: Claims = { roles, salonId: roles.includes("owner") ? current.salonId : null, stylistId: null };
    await setClaims(uid, claims);
    await ref.update({ uid: null, formerUid: uid, active: false, updatedAt: FieldValue.serverTimestamp() });
    await col.users().doc(uid).set({ roles: roles.length > 0 ? roles : ["customer"], salonId: claims.salonId, stylistId: null }, { merge: true });
    return claims;
  }
  const claims = await linkStylist(uid, input.salonId, input.stylistId, current);
  await ref.update({ active: true });
  return claims;
}

const DEFAULT_HOURS = {
  "1": [{ start: "09:00", end: "20:00" }],
  "2": [{ start: "09:00", end: "20:00" }],
  "3": [{ start: "09:00", end: "20:00" }],
  "4": [{ start: "09:00", end: "20:00" }],
  "5": [{ start: "09:00", end: "20:00" }],
  "6": [{ start: "09:00", end: "16:00" }],
  "7": [],
};

export async function createSalon(caller: Caller, input: Input<"createSalon">): Promise<{ salonId: string }> {
  const record = await auth.getUser(caller.uid);
  const current = claimsOf(record);
  if (current.salonId) throw notAllowed("You already belong to a salon");
  const ref = col.salons().doc();
  const slug = `${input.name}-${input.city}`
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "dj")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  await ref.set({
    name: input.name,
    slug: `${slug}-${ref.id.slice(0, 4).toLowerCase()}`,
    description: "",
    address: input.address,
    city: input.city,
    geo: new GeoPoint(input.lat, input.lng),
    geohash: geohash.encode(input.lat, input.lng, 9),
    phone: input.phone,
    photos: [],
    coverPhoto: null,
    accentColor: SALON_ACCENTS[0],
    rating: { avg: 0, count: 0 },
    priceLevel: 2,
    workingHours: DEFAULT_HOURS,
    timezone: DEFAULT_ZONE,
    rules: DEFAULT_RULES,
    ownerUid: caller.uid,
    published: false,
    categories: [],
    minPrice: null,
    searchKeywords: searchKeywords(input.name, input.city, []),
    nextFreeAt: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  const claims: Claims = { roles: [...new Set([...current.roles, "owner"])], salonId: ref.id, stylistId: null };
  await setClaims(caller.uid, claims);
  await col.users().doc(caller.uid).set({ roles: claims.roles, salonId: ref.id, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  return { salonId: ref.id };
}

async function deleteCollection(query: FirebaseFirestore.Query): Promise<void> {
  for (;;) {
    const snap = await query.limit(300).get();
    if (snap.empty) return;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
}

/**
 * Brisanje naloga (zahtev Google Play-a): otkazuje buduće termine, anonimizuje prošle,
 * briše profil, sanduče, listu čekanja, fotografiju i nalog. Recenzije ostaju bez imena.
 */
export async function deleteAccount(caller: Caller, nowMs = Date.now()): Promise<void> {
  const uid = caller.uid;
  const appointments = await col.appointments().where("customerUid", "==", uid).get();
  for (const doc of appointments.docs) {
    const appointment = toAppointment(doc);
    try {
      if (appointment.status === "held") {
        await releaseHold(caller, { appointmentId: appointment.id }, nowMs);
        continue;
      }
      if (appointment.status === "confirmed" && appointment.start > nowMs) {
        // Brisanje naloga uvek oslobađa buduće termine, bez obzira na rok za otkazivanje.
        await cancelAppointment(
          { ...caller, roles: [], salonId: null },
          { appointmentId: appointment.id, reason: "account_deleted" },
          nowMs,
          { bypassDeadline: true },
        );
      }
    } catch (error) {
      logger.warn("Could not cancel appointment during account deletion", { id: appointment.id, error: String(error) });
    }
    await doc.ref.update({
      customerUid: null,
      customerName: "",
      customerPhone: null,
      note: "",
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  const reviews = await db.collectionGroup("reviews").where("uid", "==", uid).get();
  await Promise.all(reviews.docs.map((d) => d.ref.update({ uid: null, authorName: "" })));

  await deleteCollection(col.waitlist().where("uid", "==", uid));
  await deleteCollection(col.inbox(uid));

  const record = await auth.getUser(uid).catch(() => null);
  const claims = record ? claimsOf(record) : { roles: [], salonId: null, stylistId: null };
  if (claims.salonId && claims.stylistId) {
    await col.stylists(claims.salonId).doc(claims.stylistId).update({ uid: null, active: false });
  }
  if (claims.salonId && claims.roles.includes("owner")) {
    // Salon bez vlasnika se skida sa liste da klijenti ne bi zakazivali kod nekog ko ne odgovara.
    await col.salons().doc(claims.salonId).update({ ownerUid: null, published: false });
  }
  if (record?.phoneNumber) {
    await col.invites().doc(record.phoneNumber).delete().catch(() => undefined);
  }

  try {
    await storage.bucket().deleteFiles({ prefix: `users/${uid}/` });
  } catch (error) {
    logger.warn("Could not delete user files", { uid, error: String(error) });
  }
  await col.users().doc(uid).delete();
  await col.rateLimits().where("__name__", ">=", `${uid}_`).where("__name__", "<", `${uid}_`).get()
    .then((snap) => Promise.all(snap.docs.map((d) => d.ref.delete())))
    .catch(() => undefined);
  await auth.deleteUser(uid);
  logger.info("Account deleted", { uid, at: Timestamp.fromMillis(nowMs).toDate().toISOString() });
}
