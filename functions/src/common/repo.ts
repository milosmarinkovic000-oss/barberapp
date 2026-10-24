import type { DocumentData, DocumentReference, DocumentSnapshot, Transaction } from "firebase-admin/firestore";
import { Timestamp } from "firebase-admin/firestore";
import { db } from "./firebase";
import { AppError, notFound } from "./errors";
import type {
  AppointmentSource,
  AppointmentStatus,
  BookedService,
  BusyInterval,
  Salon,
  SalonRules,
  Service,
  ServiceCategory,
  Stylist,
  WeeklyHours,
} from "../domain/types";
import type { Provider } from "../domain/types";
import { DEFAULT_RULES, PROVIDERS } from "../domain/types";
import { DEFAULT_ZONE } from "../domain/time";

export const col = {
  users: () => db.collection("users"),
  salons: () => db.collection("salons"),
  services: (salonId: string) => db.collection("salons").doc(salonId).collection("services"),
  stylists: (salonId: string) => db.collection("salons").doc(salonId).collection("stylists"),
  timeOff: (salonId: string, stylistId: string) =>
    db.collection("salons").doc(salonId).collection("stylists").doc(stylistId).collection("timeOff"),
  days: (salonId: string, stylistId: string) =>
    db.collection("salons").doc(salonId).collection("stylists").doc(stylistId).collection("days"),
  reviews: (salonId: string) => db.collection("salons").doc(salonId).collection("reviews"),
  appointments: () => db.collection("appointments"),
  waitlist: () => db.collection("waitlist"),
  inbox: (uid: string) => db.collection("users").doc(uid).collection("inbox"),
  invites: () => db.collection("invites"),
  integrations: () => db.collection("integrations"),
  oauthStates: () => db.collection("oauthStates"),
  busyCache: () => db.collection("busyCache"),
  webhookEvents: () => db.collection("webhookEvents"),
  rateLimits: () => db.collection("rateLimits"),
};

function num(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function str(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function strArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
}

function hours(value: unknown): WeeklyHours {
  if (value === null || typeof value !== "object") return {};
  const result: WeeklyHours = {};
  for (const [key, ranges] of Object.entries(value as Record<string, unknown>)) {
    if (!["1", "2", "3", "4", "5", "6", "7"].includes(key) || !Array.isArray(ranges)) continue;
    result[key as keyof WeeklyHours] = (ranges as unknown[])
      .filter((r): r is { start: string; end: string } => {
        const range = r as { start?: unknown; end?: unknown } | null;
        return typeof range?.start === "string" && typeof range?.end === "string";
      })
      .map((r) => ({ start: r.start, end: r.end }));
  }
  return result;
}

export function parseRules(value: unknown): SalonRules {
  const v = (value ?? {}) as Record<string, unknown>;
  return {
    cancelHours: num(v.cancelHours, DEFAULT_RULES.cancelHours),
    minNoticeMinutes: num(v.minNoticeMinutes, DEFAULT_RULES.minNoticeMinutes),
    horizonDays: num(v.horizonDays, DEFAULT_RULES.horizonDays),
    slotStepMin: num(v.slotStepMin, DEFAULT_RULES.slotStepMin),
    bufferMin: num(v.bufferMin, DEFAULT_RULES.bufferMin),
  };
}

export function toSalon(snap: DocumentSnapshot): Salon {
  const d = snap.data() ?? {};
  return {
    id: snap.id,
    name: str(d.name),
    phone: str(d.phone),
    address: str(d.address),
    city: str(d.city),
    timezone: str(d.timezone, DEFAULT_ZONE),
    workingHours: hours(d.workingHours),
    rules: parseRules(d.rules),
    ownerUid: typeof d.ownerUid === "string" ? d.ownerUid : null,
    published: d.published === true,
  };
}

export function toService(snap: DocumentSnapshot): Service {
  const d = snap.data() ?? {};
  return {
    id: snap.id,
    category: str(d.category, "haircut") as ServiceCategory,
    name: str(d.name),
    description: str(d.description),
    durationMin: num(d.durationMin, 30),
    price: num(d.price, 0),
    stylistIds: strArray(d.stylistIds),
    active: d.active !== false,
    order: num(d.order, 0),
  };
}

export function toStylist(snap: DocumentSnapshot): Stylist {
  const d = snap.data() ?? {};
  return {
    id: snap.id,
    name: str(d.name),
    uid: typeof d.uid === "string" ? d.uid : null,
    active: d.active !== false,
    workingHours: d.workingHours === null || d.workingHours === undefined ? null : hours(d.workingHours),
    breaks: hours(d.breaks),
    order: num(d.order, 0),
    enabledProviders: enabledProviders(d.integrations),
  };
}

function enabledProviders(value: unknown): Provider[] {
  if (value === null || typeof value !== "object") return [];
  const map = value as Record<string, { connected?: unknown; enabled?: unknown } | undefined>;
  return PROVIDERS.filter((p) => map[p]?.connected === true && map[p]?.enabled === true);
}

export function toBusy(value: unknown): BusyInterval[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((v): v is Record<string, unknown> => v !== null && typeof v === "object")
    .map((v) => ({
      id: str(v.id),
      start: num(v.start, 0),
      end: num(v.end, 0),
      kind: str(v.kind, "appt") as BusyInterval["kind"],
      ...(typeof v.expiresAt === "number" ? { expiresAt: v.expiresAt } : {}),
    }))
    .filter((v) => v.end > v.start);
}

export interface AppointmentDoc {
  id: string;
  salonId: string;
  salonName: string;
  stylistId: string;
  stylistName: string;
  customerUid: string | null;
  customerName: string;
  customerPhone: string | null;
  services: BookedService[];
  start: number;
  end: number;
  durationMin: number;
  totalPrice: number;
  status: AppointmentStatus;
  holdExpiresAt: number | null;
  note: string;
  source: AppointmentSource;
  externalIds: Record<string, string>;
  anyStylist: boolean;
  dateKey: string;
  remindersSent: { h24: boolean; h2: boolean; review: boolean };
  reviewed: boolean;
}

function millis(value: unknown): number {
  if (value instanceof Timestamp) return value.toMillis();
  if (typeof value === "number") return value;
  return 0;
}

export function toAppointment(snap: DocumentSnapshot): AppointmentDoc {
  const d = snap.data() ?? {};
  const reminders = (d.remindersSent ?? {}) as Record<string, unknown>;
  return {
    id: snap.id,
    salonId: str(d.salonId),
    salonName: str(d.salonName),
    stylistId: str(d.stylistId),
    stylistName: str(d.stylistName),
    customerUid: typeof d.customerUid === "string" ? d.customerUid : null,
    customerName: str(d.customerName),
    customerPhone: typeof d.customerPhone === "string" ? d.customerPhone : null,
    services: Array.isArray(d.services)
      ? (d.services as Record<string, unknown>[]).map((s) => ({
          id: str(s.id),
          name: str(s.name),
          price: num(s.price, 0),
          durationMin: num(s.durationMin, 0),
        }))
      : [],
    start: millis(d.start),
    end: millis(d.end),
    durationMin: num(d.durationMin, 0),
    totalPrice: num(d.totalPrice, 0),
    status: str(d.status, "confirmed") as AppointmentStatus,
    holdExpiresAt: d.holdExpiresAt ? millis(d.holdExpiresAt) : null,
    note: str(d.note),
    source: str(d.source, "app") as AppointmentSource,
    externalIds: (d.externalIds ?? {}) as Record<string, string>,
    anyStylist: d.anyStylist === true,
    dateKey: str(d.dateKey),
    remindersSent: {
      h24: reminders.h24 === true,
      h2: reminders.h2 === true,
      review: reminders.review === true,
    },
    reviewed: d.reviewed === true,
  };
}

type Reader = Pick<Transaction, "get"> | null;

async function read(ref: DocumentReference, tx: Reader): Promise<DocumentSnapshot> {
  return tx ? tx.get(ref) : ref.get();
}

export async function loadSalon(salonId: string, tx: Reader = null): Promise<Salon> {
  const snap = await read(col.salons().doc(salonId), tx);
  if (!snap.exists) throw notFound("Salon");
  return toSalon(snap);
}

export async function loadAppointment(appointmentId: string, tx: Reader = null): Promise<AppointmentDoc> {
  const snap = await read(col.appointments().doc(appointmentId), tx);
  if (!snap.exists) throw notFound("Appointment");
  return toAppointment(snap);
}

export async function loadServices(salonId: string, serviceIds: string[]): Promise<Service[]> {
  const unique = [...new Set(serviceIds)];
  const snaps = await db.getAll(...unique.map((id) => col.services(salonId).doc(id)));
  const services = snaps.filter((s) => s.exists).map(toService);
  if (services.length !== unique.length || services.some((s) => !s.active)) {
    throw new AppError("VALIDATION", "Unknown or inactive service");
  }
  return services.sort((a, b) => a.order - b.order);
}

export async function loadStylists(salonId: string): Promise<Stylist[]> {
  const snaps = await col.stylists(salonId).get();
  return snaps.docs.map(toStylist).sort((a, b) => a.order - b.order);
}

export async function loadStylist(salonId: string, stylistId: string, tx: Reader = null): Promise<Stylist> {
  const snap = await read(col.stylists(salonId).doc(stylistId), tx);
  if (!snap.exists) throw notFound("Stylist");
  return toStylist(snap);
}

/** Frizeri koji su aktivni i rade SVE izabrane usluge. */
export function eligibleStylists(stylists: Stylist[], services: Service[]): Stylist[] {
  return stylists.filter((st) => st.active && services.every((s) => s.stylistIds.includes(st.id)));
}

export function bookedServices(services: Service[]): BookedService[] {
  return services.map((s) => ({ id: s.id, name: s.name, price: s.price, durationMin: s.durationMin }));
}

export function totals(services: Service[]): { durationMin: number; totalPrice: number } {
  return {
    durationMin: services.reduce((sum, s) => sum + s.durationMin, 0),
    totalPrice: services.reduce((sum, s) => sum + s.price, 0),
  };
}

export function dataOf(snap: DocumentSnapshot): DocumentData {
  return snap.data() ?? {};
}
