import { z } from "zod";
import { AppError } from "./errors";
import { isDateKey } from "../domain/time";
import { PROVIDERS } from "../domain/types";

const id = z.string().min(1).max(128).regex(/^[A-Za-z0-9_-]+$/);
const dateKey = z.string().refine(isDateKey, "Invalid date");
const epochMs = z.number().int().min(1_500_000_000_000).max(4_000_000_000_000);
const phone = z.string().regex(/^\+[1-9]\d{7,14}$/, "Phone must be in E.164 format");
const serviceIds = z.array(id).min(1).max(8);
const shortText = (max: number) => z.string().trim().max(max);

export const schemas = {
  getAvailability: z.object({
    salonId: id,
    serviceIds,
    stylistId: id.nullable().default(null),
    fromDate: dateKey,
    days: z.number().int().min(1).max(14).default(14),
    excludeAppointmentId: id.optional(),
  }),
  holdSlot: z.object({
    salonId: id,
    serviceIds,
    stylistId: id.nullable().default(null),
    start: epochMs,
    replaceHoldId: id.optional(),
  }),
  confirmBooking: z.object({
    appointmentId: id,
    note: shortText(500).default(""),
    waitlistId: id.optional(),
  }),
  releaseHold: z.object({ appointmentId: id }),
  cancelAppointment: z.object({
    appointmentId: id,
    reason: shortText(300).default(""),
  }),
  rescheduleAppointment: z.object({
    appointmentId: id,
    start: epochMs,
    stylistId: id.nullable().default(null),
  }),
  createManualBooking: z.object({
    salonId: id,
    stylistId: id,
    serviceIds,
    start: epochMs,
    customerName: z.string().trim().min(1).max(80),
    customerPhone: phone.nullable().default(null),
    note: shortText(500).default(""),
    walkIn: z.boolean().default(false),
  }),
  blockTime: z
    .object({
      salonId: id,
      stylistId: id,
      start: epochMs,
      end: epochMs,
      kind: z.enum(["block", "vacation", "sick", "other"]).default("block"),
      reason: shortText(200).default(""),
    })
    .refine((v) => v.end > v.start, "End must be after start")
    .refine((v) => v.end - v.start <= 62 * 86_400_000, "Time off can be at most 62 days"),
  removeTimeOff: z.object({ salonId: id, stylistId: id, timeOffId: id }),
  updateAppointmentStatus: z.object({
    appointmentId: id,
    status: z.enum(["confirmed", "completed", "no_show", "canceled_by_salon"]),
    reason: shortText(300).default(""),
  }),
  joinWaitlist: z.object({
    salonId: id,
    dateKey,
    serviceIds,
    stylistId: id.nullable().default(null),
  }),
  leaveWaitlist: z.object({ waitlistId: id }),
  submitReview: z.object({
    appointmentId: id,
    rating: z.number().int().min(1).max(5),
    text: shortText(1000).default(""),
  }),
  replyToReview: z.object({
    salonId: id,
    reviewId: id,
    text: z.string().trim().min(1).max(1000),
  }),
  inviteStylist: z.object({
    salonId: id,
    phone,
    name: z.string().trim().min(1).max(80),
  }),
  setUserRole: z.object({
    salonId: id,
    stylistId: id,
    role: z.enum(["stylist", "customer"]),
  }),
  syncAccount: z.object({
    language: z.enum(["sr", "en"]).optional(),
    name: shortText(60).optional(),
  }),
  createSalon: z.object({
    name: z.string().trim().min(2).max(80),
    city: z.string().trim().min(2).max(60),
    address: z.string().trim().min(3).max(160),
    phone,
    lat: z.number().min(-90).max(90),
    lng: z.number().min(-180).max(180),
  }),
  deleteAccount: z.object({ confirm: z.literal(true) }),
  getSalonStats: z.object({
    salonId: id,
    period: z.enum(["day", "week", "month"]),
    date: dateKey,
  }),
  startIntegrationOAuth: z.object({ salonId: id, stylistId: id, provider: z.enum(PROVIDERS) }),
  setIntegrationEnabled: z.object({
    salonId: id,
    stylistId: id,
    provider: z.enum(PROVIDERS),
    enabled: z.boolean(),
  }),
  disconnectIntegration: z.object({ salonId: id, stylistId: id, provider: z.enum(PROVIDERS) }),
} as const;

export type Schemas = typeof schemas;
export type Input<K extends keyof Schemas> = z.output<Schemas[K]>;

export function parse<K extends keyof Schemas>(name: K, data: unknown): Input<K> {
  const result = schemas[name].safeParse(data ?? {});
  if (!result.success) {
    const issues = result.error.issues.map((i) => ({ path: i.path.join("."), message: i.message }));
    throw new AppError("VALIDATION", "Invalid input", { issues });
  }
  return result.data as Input<K>;
}
