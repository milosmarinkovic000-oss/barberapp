import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import type { AppointmentDoc } from "../common/repo";
import { col } from "../common/repo";
import type { Salon, Stylist } from "../domain/types";
import type { AppointmentPayload } from "./provider";
import { isProviderReady, providerFor } from "./registry";
import { loadConnection } from "./store";

async function payloadFor(appointment: AppointmentDoc, salon: Salon): Promise<AppointmentPayload> {
  let email: string | null = null;
  if (appointment.customerUid) {
    const user = await col.users().doc(appointment.customerUid).get();
    email = typeof user.data()?.email === "string" ? (user.data()?.email as string) : null;
  }
  const services = appointment.services.map((s) => s.name).join(", ");
  return {
    id: appointment.id,
    title: `${appointment.customerName || "Klijent"} · ${services}`,
    description: [services, appointment.note].filter((t) => t.length > 0).join("\n"),
    start: appointment.start,
    end: appointment.end,
    timezone: salon.timezone,
    customerName: appointment.customerName || "Klijent",
    customerEmail: email,
    customerPhone: appointment.customerPhone,
    location: `${salon.name}, ${salon.address}, ${salon.city}`,
  };
}

/**
 * Upisuje, menja ili briše termin u spoljnim kalendarima frizera. Termin koji je stigao iz
 * nekog provajdera ne vraća se nazad tom istom provajderu. Greške ne ruše zakazivanje:
 * stanje se beleži u polju externalSync termina.
 */
export async function syncAppointment(
  appointment: AppointmentDoc,
  action: "upsert" | "delete",
  salon: Salon,
  stylist: Stylist,
): Promise<void> {
  const targets = stylist.enabledProviders.filter((p) => p !== appointment.source && isProviderReady(p));
  if (targets.length === 0) return;
  const payload = await payloadFor(appointment, salon);
  const externalIds: Record<string, string | FieldValue> = {};
  const status: Record<string, string> = {};

  for (const provider of targets) {
    const existing = appointment.externalIds[provider];
    try {
      const connection = await loadConnection(salon.id, provider, stylist.id);
      if (!connection) continue;
      const api = providerFor(provider);
      if (action === "delete") {
        if (existing) await api.deleteEvent(connection, existing);
        status[provider] = "deleted";
        continue;
      }
      const id = existing ? await api.updateEvent(connection, existing, payload) : await api.createEvent(connection, payload);
      if (id) {
        externalIds[provider] = id;
        status[provider] = "ok";
      } else {
        if (existing) externalIds[provider] = FieldValue.delete();
        status[provider] = "skipped";
      }
    } catch (error) {
      status[provider] = "error";
      logger.warn("External calendar sync failed", { appointmentId: appointment.id, provider, error: String(error) });
    }
  }

  const update: Record<string, unknown> = {};
  for (const [provider, value] of Object.entries(externalIds)) update[`externalIds.${provider}`] = value;
  for (const [provider, value] of Object.entries(status)) update[`externalSync.${provider}`] = value;
  if (Object.keys(update).length > 0) {
    await col.appointments().doc(appointment.id).update(update);
  }
}
