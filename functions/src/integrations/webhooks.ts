import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { db } from "../common/firebase";
import type { AppointmentDoc } from "../common/repo";
import { col, loadSalon, loadStylist, toAppointment } from "../common/repo";
import type { Provider } from "../domain/types";
import { dateKeyOf } from "../domain/time";
import { dayRef, readDaysTx, withInterval, withoutInterval, writeDay } from "../booking/days";
import { afterBookingChange } from "../booking/effects";
import { refreshStylistBusy } from "./busy";
import { sha256Hex } from "./crypto";
import type { ExternalBooking, WebhookEvent, WebhookRequest } from "./provider";
import { WebhookSignatureError } from "./provider";
import { providerFor } from "./registry";
import { findByAccount, loadConnection } from "./store";
import type { CalcomProvider } from "./calcom";

export interface WebhookResponse {
  status: number;
  body: string;
}

/** Idempotentnost: svaki događaj se obrađuje tačno jednom (create() pada ako već postoji). */
async function claimEvent(provider: Provider, eventId: string): Promise<boolean> {
  try {
    await col.webhookEvents().doc(`${provider}_${sha256Hex(eventId).slice(0, 40)}`).create({
      provider,
      eventId,
      receivedAt: FieldValue.serverTimestamp(),
    });
    return true;
  } catch (error) {
    if ((error as { code?: number }).code === 6) return false;
    throw error;
  }
}

async function findByExternalId(provider: Provider, externalId: string): Promise<AppointmentDoc | null> {
  const snap = await col.appointments().where(`externalIds.${provider}`, "==", externalId).limit(1).get();
  return snap.empty ? null : toAppointment(snap.docs[0]);
}

/** Uvoz termina zakazanog direktno u spoljnom kalendaru, da dostupnost bude tačna. */
async function importBooking(provider: Provider, salonId: string, stylistId: string, booking: ExternalBooking): Promise<void> {
  if (await findByExternalId(provider, booking.externalId)) return;
  const salon = await loadSalon(salonId);
  const stylist = await loadStylist(salonId, stylistId);
  const zone = salon.timezone;
  const dateKey = dateKeyOf(booking.start, zone);
  const ref = col.appointments().doc();
  const durationMin = Math.round((booking.end - booking.start) / 60_000);
  const nowMs = Date.now();

  const appointment = await db.runTransaction(async (tx) => {
    const r = dayRef(salonId, stylistId, dateKey);
    const days = await readDaysTx(tx, [r]);
    const busy = days.get(r.path) ?? [];
    const conflict = busy.some((b) => (b.kind === "appt" || (b.kind === "hold" && (b.expiresAt ?? 0) > nowMs)) && b.start < booking.end && booking.start < b.end);
    writeDay(tx, r, withInterval(busy, { id: ref.id, start: booking.start, end: booking.end, kind: "appt" }, nowMs));
    const services = [{ id: `${provider}-event`, name: booking.title || provider, price: 0, durationMin }];
    tx.create(ref, {
      salonId,
      salonName: salon.name,
      salonPhone: salon.phone,
      salonAddress: `${salon.address}, ${salon.city}`,
      stylistId,
      stylistName: stylist.name,
      customerUid: null,
      customerName: booking.customerName,
      customerPhone: null,
      services,
      start: Timestamp.fromMillis(booking.start),
      end: Timestamp.fromMillis(booking.end),
      durationMin,
      totalPrice: 0,
      status: "confirmed",
      holdExpiresAt: null,
      note: "",
      source: provider,
      externalIds: { [provider]: booking.externalId },
      anyStylist: false,
      dateKey,
      conflict,
      remindersSent: { h24: true, h2: true, review: true },
      reviewed: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return {
      id: ref.id,
      salonId,
      salonName: salon.name,
      stylistId,
      stylistName: stylist.name,
      customerUid: null,
      customerName: booking.customerName,
      customerPhone: null,
      services,
      start: booking.start,
      end: booking.end,
      durationMin,
      totalPrice: 0,
      status: "confirmed" as const,
      holdExpiresAt: null,
      note: "",
      source: provider,
      externalIds: { [provider]: booking.externalId },
      anyStylist: false,
      dateKey,
      remindersSent: { h24: true, h2: true, review: true },
      reviewed: false,
    };
  });
  await afterBookingChange(appointment, { kind: "confirmed", actor: "external" }, null);
}

async function cancelExternal(provider: Provider, externalId: string): Promise<void> {
  const found = await findByExternalId(provider, externalId);
  if (!found || (found.status !== "confirmed" && found.status !== "held")) return;
  const nowMs = Date.now();
  const status = found.source === provider ? "canceled_by_customer" : "canceled_by_salon";
  await db.runTransaction(async (tx) => {
    const r = dayRef(found.salonId, found.stylistId, found.dateKey);
    const days = await readDaysTx(tx, [r]);
    writeDay(tx, r, withoutInterval(days.get(r.path) ?? [], found.id, nowMs));
    tx.update(col.appointments().doc(found.id), {
      status,
      [`externalIds.${provider}`]: FieldValue.delete(),
      canceledAt: FieldValue.serverTimestamp(),
      canceledBy: provider,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  const updated = { ...found, status, externalIds: Object.fromEntries(Object.entries(found.externalIds).filter(([k]) => k !== provider)) } as AppointmentDoc;
  await afterBookingChange(updated, { kind: "canceled", actor: status === "canceled_by_customer" ? "customer" : "external", previous: updated }, null);
}

async function rescheduleExternal(provider: Provider, salonId: string, stylistId: string, booking: ExternalBooking): Promise<void> {
  let previousId = booking.previousExternalId;
  if (!previousId && provider === "calcom") {
    const connection = await loadConnection(salonId, provider, stylistId);
    if (connection) {
      previousId = (await (providerFor("calcom") as CalcomProvider).getBooking(connection, booking.externalId)).rescheduledFromUid;
    }
  }
  const found = previousId ? await findByExternalId(provider, previousId) : null;
  if (!found) {
    await importBooking(provider, salonId, stylistId, booking);
    return;
  }
  const salon = await loadSalon(found.salonId);
  const nowMs = Date.now();
  const dateKey = dateKeyOf(booking.start, salon.timezone);
  await db.runTransaction(async (tx) => {
    const oldRef = dayRef(found.salonId, found.stylistId, found.dateKey);
    const newRef = dayRef(found.salonId, found.stylistId, dateKey);
    const days = await readDaysTx(tx, [oldRef, newRef]);
    if (oldRef.path !== newRef.path) writeDay(tx, oldRef, withoutInterval(days.get(oldRef.path) ?? [], found.id, nowMs));
    const base = (days.get(newRef.path) ?? []).filter((b) => b.id !== found.id);
    writeDay(tx, newRef, withInterval(base, { id: found.id, start: booking.start, end: booking.end, kind: "appt" }, nowMs));
    tx.update(col.appointments().doc(found.id), {
      start: Timestamp.fromMillis(booking.start),
      end: Timestamp.fromMillis(booking.end),
      dateKey,
      [`externalIds.${provider}`]: booking.externalId,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  const updated = { ...found, start: booking.start, end: booking.end, dateKey, externalIds: { ...found.externalIds, [provider]: booking.externalId } };
  await afterBookingChange(updated, { kind: "rescheduled", actor: "external", previous: found }, null);
}

async function process(provider: Provider, event: WebhookEvent): Promise<void> {
  if (event.kind === "ignored") return;
  const target = await findByAccount(provider, event.accountId);
  if (!target) {
    logger.info("Webhook for unknown account ignored", { provider });
    return;
  }
  switch (event.kind) {
    case "created":
      await importBooking(provider, target.salonId, target.stylistId, event.booking);
      break;
    case "canceled":
      await cancelExternal(provider, event.externalId);
      break;
    case "rescheduled":
      await rescheduleExternal(provider, target.salonId, target.stylistId, event.booking);
      break;
    case "changed": {
      const salon = await loadSalon(target.salonId);
      const stylist = await loadStylist(target.salonId, target.stylistId);
      await refreshStylistBusy(target.salonId, stylist, salon.timezone);
      break;
    }
  }
}

export async function handleWebhook(provider: Provider, request: WebhookRequest): Promise<WebhookResponse> {
  let event: WebhookEvent;
  try {
    event = providerFor(provider).parseWebhook(request);
  } catch (error) {
    if (error instanceof WebhookSignatureError) return { status: 401, body: "invalid signature" };
    return { status: 400, body: "bad request" };
  }
  if (!(await claimEvent(provider, event.eventId))) return { status: 200, body: "duplicate" };
  try {
    await process(provider, event);
  } catch (error) {
    // Događaj ostaje zabeležen; ponovno slanje istog događaja se neće obraditi dvaput, pa ga
    // brišemo da bi provajder mogao da ga pošalje ponovo.
    await col.webhookEvents().doc(`${provider}_${sha256Hex(event.eventId).slice(0, 40)}`).delete();
    logger.error("Webhook processing failed", { provider, error: String(error) });
    return { status: 500, body: "error" };
  }
  return { status: 200, body: "ok" };
}
