import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, getDocs, collection, query, setDoc, updateDoc, where } from "firebase/firestore";
import { ref, uploadBytes, getBytes } from "firebase/storage";

let env: RulesTestEnvironment;

function hostPort(value: string | undefined, fallback: number): { host: string; port: number } {
  const [host, port] = (value ?? `127.0.0.1:${fallback}`).split(":");
  return { host, port: Number(port) };
}

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-termin-rules",
    firestore: {
      ...hostPort(process.env.FIRESTORE_EMULATOR_HOST, 8080),
      rules: readFileSync(resolve(__dirname, "../../../firestore.rules"), "utf8"),
    },
    storage: {
      ...hostPort(process.env.FIREBASE_STORAGE_EMULATOR_HOST, 9199),
      rules: readFileSync(resolve(__dirname, "../../../storage.rules"), "utf8"),
    },
  });
});

afterAll(async () => {
  await env?.cleanup();
});

const salonData = {
  name: "Studio",
  description: "",
  published: true,
  accentColor: "#8C6A3F",
  photos: [],
  workingHours: {},
  rules: { cancelHours: 12, minNoticeMinutes: 60, horizonDays: 30, slotStepMin: 15, bufferMin: 10 },
  ownerUid: "owner1",
  rating: { avg: 4.5, count: 10 },
};

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "salons/s1"), salonData);
    await setDoc(doc(db, "salons/s2"), { ...salonData, published: false, ownerUid: "owner2" });
    await setDoc(doc(db, "salons/s1/services/svc"), { name: "Šišanje", price: 1500 });
    await setDoc(doc(db, "salons/s1/stylists/st1"), { name: "Ana", uid: "stylist1" });
    await setDoc(doc(db, "salons/s1/stylists/st1/days/2026-11-10"), { intervals: [] });
    await setDoc(doc(db, "salons/s1/stylists/st1/timeOff/t1"), { reason: "odmor" });
    await setDoc(doc(db, "appointments/a1"), { salonId: "s1", stylistId: "st1", customerUid: "alice", status: "confirmed" });
    await setDoc(doc(db, "appointments/a2"), { salonId: "s2", stylistId: "x", customerUid: "bob", status: "confirmed" });
    await setDoc(doc(db, "users/alice"), { name: "Alice", roles: ["customer"], favoriteSalonIds: [] });
    await setDoc(doc(db, "users/alice/inbox/m1"), { title: "Hej", read: false });
    await setDoc(doc(db, "waitlist/w1"), { uid: "alice", salonId: "s1" });
    await setDoc(doc(db, "integrations/s1_google"), { stylists: {} });
  });
});

const customer = () => env.authenticatedContext("alice").firestore();
const otherCustomer = () => env.authenticatedContext("bob").firestore();
const owner = () => env.authenticatedContext("owner1", { roles: ["owner"], salonId: "s1" }).firestore();
const stylist = () => env.authenticatedContext("stylist1", { roles: ["stylist"], salonId: "s1", stylistId: "st1" }).firestore();
const foreignOwner = () => env.authenticatedContext("owner2", { roles: ["owner"], salonId: "s2" }).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

describe("saloni", () => {
  it("svako čita objavljen salon, usluge i frizere", async () => {
    await assertSucceeds(getDoc(doc(anonymous(), "salons/s1")));
    await assertSucceeds(getDoc(doc(anonymous(), "salons/s1/services/svc")));
    await assertSucceeds(getDoc(doc(anonymous(), "salons/s1/stylists/st1")));
    await assertSucceeds(getDocs(query(collection(anonymous(), "salons"), where("published", "==", true))));
  });

  it("neobjavljen salon vidi samo njegovo osoblje", async () => {
    await assertFails(getDoc(doc(customer(), "salons/s2")));
    await assertSucceeds(getDoc(doc(foreignOwner(), "salons/s2")));
  });

  it("vlasnik menja podatke salona, ali ne ocenu ni vlasnika", async () => {
    await assertSucceeds(updateDoc(doc(owner(), "salons/s1"), { description: "Novi opis", published: false }));
    await assertFails(updateDoc(doc(owner(), "salons/s1"), { rating: { avg: 5, count: 99 } }));
    await assertFails(updateDoc(doc(owner(), "salons/s1"), { ownerUid: "neko" }));
    await assertFails(updateDoc(doc(owner(), "salons/s1"), { accentColor: "red" }));
    await assertFails(updateDoc(doc(foreignOwner(), "salons/s1"), { description: "x" }));
    await assertFails(updateDoc(doc(customer(), "salons/s1"), { description: "x" }));
  });

  it("vlasnik ne može da postavi neispravna pravila", async () => {
    await assertFails(updateDoc(doc(owner(), "salons/s1"), { rules: { ...salonData.rules, slotStepMin: 7 } }));
    await assertFails(updateDoc(doc(owner(), "salons/s1"), { rules: { ...salonData.rules, horizonDays: 0 } }));
  });

  it("usluge menja samo vlasnik", async () => {
    const service = { name: "Brada", category: "beard", durationMin: 30, price: 800, stylistIds: [], active: true, order: 1 };
    await assertSucceeds(setDoc(doc(owner(), "salons/s1/services/new"), service));
    await assertFails(setDoc(doc(stylist(), "salons/s1/services/new2"), service));
    await assertFails(setDoc(doc(owner(), "salons/s1/services/bad"), { ...service, category: "nails" }));
  });

  it("frizer menja svoj profil, ali ne radno vreme", async () => {
    await assertSucceeds(updateDoc(doc(stylist(), "salons/s1/stylists/st1"), { bio: "Kratka biografija" }));
    await assertFails(updateDoc(doc(stylist(), "salons/s1/stylists/st1"), { workingHours: {} }));
    await assertSucceeds(updateDoc(doc(owner(), "salons/s1/stylists/st1"), { workingHours: {} }));
  });

  it("dani zauzetosti su samo za server, odsustva vidi samo osoblje", async () => {
    await assertFails(getDoc(doc(owner(), "salons/s1/stylists/st1/days/2026-11-10")));
    await assertFails(getDoc(doc(customer(), "salons/s1/stylists/st1/timeOff/t1")));
    await assertSucceeds(getDoc(doc(stylist(), "salons/s1/stylists/st1/timeOff/t1")));
    await assertFails(setDoc(doc(owner(), "salons/s1/stylists/st1/timeOff/t2"), { reason: "x" }));
  });
});

describe("termini", () => {
  it("klijent čita samo svoje termine", async () => {
    await assertSucceeds(getDoc(doc(customer(), "appointments/a1")));
    await assertFails(getDoc(doc(otherCustomer(), "appointments/a1")));
    await assertSucceeds(getDocs(query(collection(customer(), "appointments"), where("customerUid", "==", "alice"))));
    await assertFails(getDocs(query(collection(customer(), "appointments"), where("salonId", "==", "s1"))));
  });

  it("osoblje pristupa samo terminima svog salona", async () => {
    await assertSucceeds(getDoc(doc(owner(), "appointments/a1")));
    await assertSucceeds(getDoc(doc(stylist(), "appointments/a1")));
    await assertFails(getDoc(doc(owner(), "appointments/a2")));
    await assertSucceeds(getDocs(query(collection(owner(), "appointments"), where("salonId", "==", "s1"))));
  });

  it("termini se menjaju isključivo preko Cloud Functions", async () => {
    await assertFails(updateDoc(doc(customer(), "appointments/a1"), { status: "canceled_by_customer" }));
    await assertFails(updateDoc(doc(owner(), "appointments/a1"), { status: "completed" }));
    await assertFails(setDoc(doc(customer(), "appointments/new"), { salonId: "s1", customerUid: "alice" }));
    await assertFails(deleteDoc(doc(owner(), "appointments/a1")));
  });
});

describe("korisnici", () => {
  it("korisnik čita i menja svoja podešavanja, ali ne i uloge", async () => {
    await assertSucceeds(getDoc(doc(customer(), "users/alice")));
    await assertSucceeds(updateDoc(doc(customer(), "users/alice"), { favoriteSalonIds: ["s1"], language: "en" }));
    await assertFails(updateDoc(doc(customer(), "users/alice"), { roles: ["owner"] }));
    await assertFails(updateDoc(doc(customer(), "users/alice"), { salonId: "s1" }));
    await assertFails(getDoc(doc(otherCustomer(), "users/alice")));
  });

  it("sanduče: samo vlasnik, i samo oznaka pročitano", async () => {
    await assertSucceeds(updateDoc(doc(customer(), "users/alice/inbox/m1"), { read: true }));
    await assertFails(updateDoc(doc(customer(), "users/alice/inbox/m1"), { title: "x" }));
    await assertFails(getDoc(doc(otherCustomer(), "users/alice/inbox/m1")));
    await assertSucceeds(deleteDoc(doc(customer(), "users/alice/inbox/m1")));
  });

  it("lista čekanja: čita i briše samo svoj unos", async () => {
    await assertSucceeds(getDoc(doc(customer(), "waitlist/w1")));
    await assertFails(getDoc(doc(otherCustomer(), "waitlist/w1")));
    await assertFails(setDoc(doc(customer(), "waitlist/w2"), { uid: "alice" }));
    await assertSucceeds(deleteDoc(doc(customer(), "waitlist/w1")));
  });

  it("integracije i tokeni nisu dostupni nijednom klijentu", async () => {
    await assertFails(getDoc(doc(owner(), "integrations/s1_google")));
    await assertFails(getDoc(doc(customer(), "integrations/s1_google")));
  });
});

describe("Storage", () => {
  const image = new Uint8Array([0x52, 0x49, 0x46, 0x46]);

  it("vlasnik uploaduje fotografije svog salona, svako ih čita", async () => {
    const ownerStorage = env.authenticatedContext("owner1", { roles: ["owner"], salonId: "s1" }).storage();
    await assertSucceeds(uploadBytes(ref(ownerStorage, "salons/s1/photos/a.webp"), image, { contentType: "image/webp" }));
    await assertSucceeds(getBytes(ref(env.unauthenticatedContext().storage(), "salons/s1/photos/a.webp")));
    await assertFails(uploadBytes(ref(ownerStorage, "salons/s2/photos/a.webp"), image, { contentType: "image/webp" }));
    await assertFails(uploadBytes(ref(ownerStorage, "salons/s1/photos/a.txt"), image, { contentType: "text/plain" }));
  });

  it("frizer menja samo svoje fotografije; korisnik samo svoj avatar", async () => {
    const stylistStorage = env.authenticatedContext("stylist1", { roles: ["stylist"], salonId: "s1", stylistId: "st1" }).storage();
    await assertSucceeds(uploadBytes(ref(stylistStorage, "salons/s1/stylists/st1/p.webp"), image, { contentType: "image/webp" }));
    await assertFails(uploadBytes(ref(stylistStorage, "salons/s1/stylists/st2/p.webp"), image, { contentType: "image/webp" }));
    const alice = env.authenticatedContext("alice").storage();
    await assertSucceeds(uploadBytes(ref(alice, "users/alice/avatar.webp"), image, { contentType: "image/webp" }));
    await assertFails(uploadBytes(ref(alice, "users/bob/avatar.webp"), image, { contentType: "image/webp" }));
    await assertFails(getBytes(ref(env.authenticatedContext("bob").storage(), "users/alice/avatar.webp")));
  });
});
