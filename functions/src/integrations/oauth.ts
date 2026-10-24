import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { appDeepLinkBase, publicFunctionsUrl } from "../common/config";
import { AppError } from "../common/errors";
import type { Caller } from "../common/caller";
import { assertCanManageStylist } from "../common/caller";
import type { Input } from "../common/validation";
import { col, loadStylist } from "../common/repo";
import type { Provider } from "../domain/types";
import { PROVIDERS } from "../domain/types";
import { pkceChallenge, randomToken } from "./crypto";
import { isProviderReady, providerFor } from "./registry";
import { loadConnection, removeConnection, saveConnection } from "./store";

const STATE_TTL_MS = 10 * 60_000;

export function callbackUrl(): string {
  return `${publicFunctionsUrl.value().replace(/\/$/, "")}/oauthCallback`;
}

export function webhookUrl(provider: Provider): string {
  const base = publicFunctionsUrl.value().replace(/\/$/, "");
  if (provider === "google") return `${base}/googleCalendarWebhook`;
  if (provider === "calendly") return `${base}/calendlyWebhook`;
  return `${base}/calcomWebhook`;
}

function assertConfigured(provider: Provider): void {
  if (!isProviderReady(provider) || publicFunctionsUrl.value().length === 0) {
    throw new AppError("INTEGRATION_NOT_CONFIGURED", `${provider} integration is not configured on the server`, { provider });
  }
}

/** Vraća adresu za prijavu kod provajdera; aplikacija je otvara u Custom Tab-u. */
export async function startIntegrationOAuth(caller: Caller, input: Input<"startIntegrationOAuth">): Promise<{ url: string }> {
  assertCanManageStylist(caller, input.salonId, input.stylistId);
  await loadStylist(input.salonId, input.stylistId);
  assertConfigured(input.provider);
  const state = randomToken(24);
  const verifier = randomToken(48);
  await col.oauthStates().doc(state).set({
    uid: caller.uid,
    salonId: input.salonId,
    stylistId: input.stylistId,
    provider: input.provider,
    verifier,
    expiresAt: Timestamp.fromMillis(Date.now() + STATE_TTL_MS),
    createdAt: FieldValue.serverTimestamp(),
  });
  return { url: providerFor(input.provider).authorizationUrl(state, callbackUrl(), pkceChallenge(verifier)) };
}

function deepLink(provider: string, status: "ok" | "error", reason = ""): string {
  const params = new URLSearchParams({ provider, status, ...(reason ? { reason } : {}) });
  return `${appDeepLinkBase.value()}?${params.toString()}`;
}

/** Obrada povratka sa OAuth stranice; uvek preusmerava nazad u aplikaciju (deep link). */
export async function handleOAuthCallback(query: Record<string, unknown>): Promise<string> {
  const state = typeof query.state === "string" ? query.state : "";
  const code = typeof query.code === "string" ? query.code : "";
  if (!state) return deepLink("unknown", "error", "missing_state");
  const ref = col.oauthStates().doc(state);
  const snap = await ref.get();
  const data = snap.data();
  if (!snap.exists || !data) return deepLink("unknown", "error", "invalid_state");
  await ref.delete();
  const provider = data.provider as Provider;
  if (!PROVIDERS.includes(provider)) return deepLink("unknown", "error", "invalid_provider");
  if ((data.expiresAt as Timestamp).toMillis() < Date.now()) return deepLink(provider, "error", "expired");
  if (!code) return deepLink(provider, "error", typeof query.error === "string" ? query.error : "denied");

  try {
    const api = providerFor(provider);
    const result = await api.exchangeCode(code, callbackUrl(), data.verifier as string);
    const salonId = data.salonId as string;
    const stylistId = data.stylistId as string;
    await saveConnection(salonId, stylistId, provider, result, null);
    try {
      const connection = await loadConnection(salonId, provider, stylistId);
      if (connection) {
        const subscriptionId = await api.subscribe(connection, webhookUrl(provider));
        if (subscriptionId) await saveConnection(salonId, stylistId, provider, result, subscriptionId);
      }
    } catch (error) {
      logger.warn("Webhook subscription failed; busy times still refresh periodically", { provider, error: String(error) });
    }
    return deepLink(provider, "ok");
  } catch (error) {
    logger.error("OAuth exchange failed", { provider, error: String(error) });
    return deepLink(provider, "error", "exchange_failed");
  }
}

export async function setIntegrationEnabled(caller: Caller, input: Input<"setIntegrationEnabled">): Promise<void> {
  assertCanManageStylist(caller, input.salonId, input.stylistId);
  const ref = col.stylists(input.salonId).doc(input.stylistId);
  const snap = await ref.get();
  const current = (snap.data()?.integrations ?? {}) as Record<string, { connected?: boolean } | undefined>;
  if (current[input.provider]?.connected !== true) {
    throw new AppError("POLICY_VIOLATION", "Integration is not connected", { reason: "NOT_CONNECTED" });
  }
  await ref.update({ [`integrations.${input.provider}.enabled`]: input.enabled });
  await col.busyCache().doc(`${input.salonId}_${input.stylistId}`).delete();
}

export async function disconnectIntegration(caller: Caller, input: Input<"disconnectIntegration">): Promise<void> {
  assertCanManageStylist(caller, input.salonId, input.stylistId);
  await removeConnection(input.salonId, input.stylistId, input.provider);
}

/** Status podešenosti na serveru, da aplikacija zna da li da ponudi povezivanje. */
export function integrationStatus(): Record<Provider, boolean> {
  return {
    google: isProviderReady("google") && publicFunctionsUrl.value().length > 0,
    calendly: isProviderReady("calendly") && publicFunctionsUrl.value().length > 0,
    calcom: isProviderReady("calcom") && publicFunctionsUrl.value().length > 0,
  };
}
