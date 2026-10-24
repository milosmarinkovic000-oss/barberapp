/** Vreme u lokalnom formatu "HH:mm" (npr. "09:00"). */
export interface TimeRange {
  start: string;
  end: string;
}

/** Radno vreme po ISO danu u nedelji: "1" = ponedeljak … "7" = nedelja. Prazan niz = ne radi. */
export type WeeklyHours = Partial<Record<"1" | "2" | "3" | "4" | "5" | "6" | "7", TimeRange[]>>;

export interface SalonRules {
  /** Rok za otkazivanje i promenu, u satima pre početka termina. */
  cancelHours: number;
  /** Najkraće vreme od trenutka zakazivanja do početka termina. */
  minNoticeMinutes: number;
  /** Koliko dana unapred može da se zakaže. */
  horizonDays: number;
  /** Korak slotova u minutima. */
  slotStepMin: number;
  /** Pauza između termina u minutima. */
  bufferMin: number;
}

export const DEFAULT_RULES: SalonRules = {
  cancelHours: 12,
  minNoticeMinutes: 60,
  horizonDays: 30,
  slotStepMin: 15,
  bufferMin: 10,
};

export type ServiceCategory = "haircut" | "blowdry" | "coloring" | "highlights" | "beard" | "treatment";

export const SERVICE_CATEGORIES: readonly ServiceCategory[] = [
  "haircut",
  "blowdry",
  "coloring",
  "highlights",
  "beard",
  "treatment",
];

export interface Service {
  id: string;
  category: ServiceCategory;
  name: string;
  description: string;
  durationMin: number;
  price: number;
  stylistIds: string[];
  active: boolean;
  order: number;
}

export interface Stylist {
  id: string;
  name: string;
  uid: string | null;
  active: boolean;
  /** Ako je null, frizer radi po radnom vremenu salona. */
  workingHours: WeeklyHours | null;
  breaks: WeeklyHours;
  order: number;
  /** Integracije koje su povezane i uključene za ovog frizera. */
  enabledProviders: Provider[];
}

export interface Salon {
  id: string;
  name: string;
  phone: string;
  address: string;
  city: string;
  timezone: string;
  workingHours: WeeklyHours;
  rules: SalonRules;
  ownerUid: string | null;
  published: boolean;
}

export type BusyKind = "hold" | "appt" | "off" | "external";

/** Zauzet interval u milisekundama (UTC epoch), poluotvoren [start, end). */
export interface BusyInterval {
  id: string;
  start: number;
  end: number;
  kind: BusyKind;
  /** Samo za hold: posle ovog trenutka interval se ignoriše. */
  expiresAt?: number;
}

export type AppointmentStatus =
  | "held"
  | "confirmed"
  | "canceled_by_customer"
  | "canceled_by_salon"
  | "completed"
  | "no_show";

export const ACTIVE_STATUSES: readonly AppointmentStatus[] = ["held", "confirmed"];

export type AppointmentSource = "app" | "manual" | "calendly" | "google" | "calcom";

export interface BookedService {
  id: string;
  name: string;
  price: number;
  durationMin: number;
}

export interface Slot {
  start: number;
  stylistIds: string[];
}

export interface DaySlots {
  date: string;
  slots: Slot[];
}

export type Provider = "google" | "calendly" | "calcom";

export const PROVIDERS: readonly Provider[] = ["google", "calendly", "calcom"];

export type Role = "customer" | "owner" | "stylist";
