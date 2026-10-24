import { logger } from "firebase-functions";
import type { CallableRequest } from "firebase-functions/https";
import { onCall, onRequest } from "firebase-functions/https";
import { onSchedule } from "firebase-functions/scheduler";
import { onDocumentWritten } from "firebase-functions/firestore";
import { setGlobalOptions } from "firebase-functions/options";
import { REGION, enforceAppCheck, integrationSecrets } from "./common/config";
import { AppError, toHttpsError } from "./common/errors";
import type { Caller } from "./common/caller";
import { requireCaller } from "./common/caller";
import type { Input, Schemas } from "./common/validation";
import { parse } from "./common/validation";
import type { Limit } from "./common/rateLimit";
import { LIMITS, enforceLocalRateLimit, enforceRateLimit } from "./common/rateLimit";
import type { AppointmentDoc } from "./common/repo";
import { computeAvailability } from "./booking/availabilityService";
import { holdSlot as holdSlotCore } from "./booking/hold";
import {
  cancelAppointment as cancelCore,
  confirmBooking as confirmCore,
  releaseHold as releaseCore,
  rescheduleAppointment as rescheduleCore,
  updateAppointmentStatus as statusCore,
} from "./booking/manage";
import { blockTime as blockCore, createManualBooking as manualCore, removeTimeOff as removeTimeOffCore } from "./booking/staff";
import { joinWaitlist as joinCore, leaveWaitlist as leaveCore, notifyWaitlist } from "./waitlist";
import { replyToReview as replyCore, submitReview as reviewCore } from "./reviews";
import {
  createSalon as createSalonCore,
  deleteAccount as deleteCore,
  inviteStylist as inviteCore,
  setUserRole as roleCore,
  syncAccount as syncCore,
} from "./accounts";
import { getSalonStats as statsCore } from "./stats";
import {
  disconnectIntegration as disconnectCore,
  handleOAuthCallback,
  integrationStatus,
  setIntegrationEnabled as enabledCore,
  startIntegrationOAuth as oauthCore,
} from "./integrations/oauth";
import { handleWebhook } from "./integrations/webhooks";
import {
  autoCompleteAppointments,
  cleanupExpiredHolds as cleanupHoldsCore,
  cleanupServerRecords,
  refreshAllNextFree,
  refreshExternalCalendars,
  sendReminders as remindersCore,
} from "./scheduled";
import { onAppointmentChange, onCatalogChange, salonNeedsRefresh } from "./triggers";
import type { Provider } from "./domain/types";

setGlobalOptions({ region: REGION, maxInstances: 20 });

type Handler<K extends keyof Schemas, R> = (caller: Caller, input: Input<K>) => Promise<R>;

interface CallableOptions {
  /** "local" = ograničenje u memoriji (za česta čitanja), inače u Firestore-u. */
  limit: Limit;
  local?: boolean;
  secrets?: boolean;
}

/** Zajednički omotač: prijava, ograničenje zahteva, zod validacija, prevod grešaka u kodove. */
function callable<K extends keyof Schemas, R>(name: K, options: CallableOptions, handler: Handler<K, R>) {
  return onCall(
    {
      enforceAppCheck,
      consumeAppCheckToken: false,
      ...(options.secrets ? { secrets: integrationSecrets } : {}),
    },
    async (request: CallableRequest<unknown>) => {
      try {
        const caller = requireCaller(request);
        if (options.local) enforceLocalRateLimit(caller.uid, name, options.limit);
        else await enforceRateLimit(caller.uid, name, options.limit);
        const input = parse(name, request.data);
        return await handler(caller, input);
      } catch (error) {
        if (!(error instanceof AppError)) logger.error(`${name} failed`, { error: String(error), stack: (error as Error)?.stack });
        throw toHttpsError(error);
      }
    },
  );
}

function serialize(a: AppointmentDoc) {
  return {
    id: a.id,
    salonId: a.salonId,
    salonName: a.salonName,
    stylistId: a.stylistId,
    stylistName: a.stylistName,
    services: a.services,
    start: a.start,
    end: a.end,
    durationMin: a.durationMin,
    totalPrice: a.totalPrice,
    status: a.status,
    note: a.note,
    dateKey: a.dateKey,
  };
}

// ---------------- Zakazivanje ----------------

export const getAvailability = callable("getAvailability", { limit: LIMITS.availability, local: true, secrets: true }, async (_c, input) =>
  computeAvailability(input),
);

export const holdSlot = callable("holdSlot", { limit: LIMITS.hold, secrets: true }, (caller, input) => holdSlotCore(caller, input));

export const confirmBooking = callable("confirmBooking", { limit: LIMITS.mutation, secrets: true }, async (caller, input) => ({
  appointment: serialize(await confirmCore(caller, input)),
}));

export const releaseHold = callable("releaseHold", { limit: LIMITS.hold }, async (caller, input) => {
  await releaseCore(caller, input);
  return { ok: true };
});

export const cancelAppointment = callable("cancelAppointment", { limit: LIMITS.mutation, secrets: true }, async (caller, input) => ({
  appointment: serialize(await cancelCore(caller, input)),
}));

export const rescheduleAppointment = callable("rescheduleAppointment", { limit: LIMITS.mutation, secrets: true }, async (caller, input) => ({
  appointment: serialize(await rescheduleCore(caller, input)),
}));

export const createManualBooking = callable("createManualBooking", { limit: LIMITS.mutation, secrets: true }, async (caller, input) => ({
  appointment: serialize(await manualCore(caller, input)),
}));

export const updateAppointmentStatus = callable("updateAppointmentStatus", { limit: LIMITS.mutation, secrets: true }, async (caller, input) => ({
  appointment: serialize(await statusCore(caller, input)),
}));

export const blockTime = callable("blockTime", { limit: LIMITS.mutation }, (caller, input) => blockCore(caller, input));

export const removeTimeOff = callable("removeTimeOff", { limit: LIMITS.mutation }, async (caller, input) => {
  const freed = await removeTimeOffCore(caller, input);
  // Oslobođeno vreme: lista čekanja za te dane dobija obaveštenje.
  await Promise.all(freed.slice(0, 31).map((k) => notifyWaitlist(input.salonId, k, null)));
  return { ok: true };
});

// ---------------- Lista čekanja i recenzije ----------------

export const joinWaitlist = callable("joinWaitlist", { limit: LIMITS.waitlist }, (caller, input) => joinCore(caller, input));

export const leaveWaitlist = callable("leaveWaitlist", { limit: LIMITS.waitlist }, async (caller, input) => {
  await leaveCore(caller, input);
  return { ok: true };
});

export const submitReview = callable("submitReview", { limit: LIMITS.review }, (caller, input) => reviewCore(caller, input));

export const replyToReview = callable("replyToReview", { limit: LIMITS.mutation }, async (caller, input) => {
  await replyCore(caller, input);
  return { ok: true };
});

// ---------------- Nalozi i uloge ----------------

export const syncAccount = callable("syncAccount", { limit: LIMITS.account }, (caller, input) => syncCore(caller, input));

export const inviteStylist = callable("inviteStylist", { limit: LIMITS.mutation }, (caller, input) => inviteCore(caller, input));

export const setUserRole = callable("setUserRole", { limit: LIMITS.mutation }, (caller, input) => roleCore(caller, input));

export const createSalon = callable("createSalon", { limit: LIMITS.account }, (caller, input) => createSalonCore(caller, input));

export const deleteAccount = callable("deleteAccount", { limit: LIMITS.account, secrets: true }, async (caller) => {
  await deleteCore(caller);
  return { ok: true };
});

export const getSalonStats = callable("getSalonStats", { limit: LIMITS.availability, local: true }, (caller, input) => statsCore(caller, input));

// ---------------- Integracije ----------------

export const startIntegrationOAuth = callable("startIntegrationOAuth", { limit: LIMITS.mutation, secrets: true }, (caller, input) =>
  oauthCore(caller, input),
);

export const setIntegrationEnabled = callable("setIntegrationEnabled", { limit: LIMITS.mutation }, async (caller, input) => {
  await enabledCore(caller, input);
  return { ok: true };
});

export const disconnectIntegration = callable("disconnectIntegration", { limit: LIMITS.mutation, secrets: true }, async (caller, input) => {
  await disconnectCore(caller, input);
  return { ok: true };
});

export const getIntegrationStatus = onCall({ enforceAppCheck, secrets: integrationSecrets }, (request) => {
  requireCaller(request);
  return integrationStatus();
});

export const oauthCallback = onRequest({ secrets: integrationSecrets }, async (req, res) => {
  const target = await handleOAuthCallback(req.query);
  res.redirect(302, target);
});

function webhook(provider: Provider) {
  return onRequest({ secrets: integrationSecrets }, async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("method not allowed");
      return;
    }
    const result = await handleWebhook(provider, { headers: req.headers, rawBody: req.rawBody });
    res.status(result.status).send(result.body);
  });
}

export const googleCalendarWebhook = webhook("google");
export const calendlyWebhook = webhook("calendly");
export const calcomWebhook = webhook("calcom");

// ---------------- Periodične funkcije ----------------

const zone = { timeZone: "Europe/Belgrade" };

export const cleanupExpiredHolds = onSchedule({ schedule: "every 5 minutes", ...zone }, async () => {
  await cleanupHoldsCore();
});

export const sendReminders = onSchedule({ schedule: "every 5 minutes", ...zone }, async () => {
  await remindersCore();
});

export const completePastAppointments = onSchedule({ schedule: "every 60 minutes", ...zone }, async () => {
  await autoCompleteAppointments();
});

export const refreshCalendars = onSchedule({ schedule: "every 10 minutes", ...zone, secrets: integrationSecrets }, async () => {
  await refreshExternalCalendars();
});

export const refreshFirstFreeSlots = onSchedule({ schedule: "every 30 minutes", ...zone, secrets: integrationSecrets }, async () => {
  await refreshAllNextFree();
});

export const cleanupRecords = onSchedule({ schedule: "every 24 hours", ...zone }, async () => {
  await cleanupServerRecords();
});

// ---------------- Firestore triggeri ----------------

export const onAppointmentWritten = onDocumentWritten({ document: "appointments/{appointmentId}", secrets: integrationSecrets }, async (event) => {
  await onAppointmentChange(event.data?.before.data(), event.data?.after.data());
});

export const onServiceWritten = onDocumentWritten({ document: "salons/{salonId}/services/{serviceId}", secrets: integrationSecrets }, async (event) => {
  await onCatalogChange(event.params.salonId);
});

export const onStylistWritten = onDocumentWritten({ document: "salons/{salonId}/stylists/{stylistId}", secrets: integrationSecrets }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  const relevant = ["active", "workingHours", "breaks"].some((k) => JSON.stringify(before?.[k] ?? null) !== JSON.stringify(after?.[k] ?? null));
  if (relevant) await onCatalogChange(event.params.salonId);
});

export const onSalonWritten = onDocumentWritten({ document: "salons/{salonId}", secrets: integrationSecrets }, async (event) => {
  if (salonNeedsRefresh(event.data?.before.data(), event.data?.after.data())) {
    await onCatalogChange(event.params.salonId);
  }
});
