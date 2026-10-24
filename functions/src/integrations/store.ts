import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { secretValue, tokenEncryptionKey } from "../common/config";
import { db } from "../common/firebase";
import { col } from "../common/repo";
import type { Provider } from "../domain/types";
import { decrypt, encrypt, sha256Hex } from "./crypto";
import type { AuthorizationResult, Connection, TokenSet } from "./provider";
import { providerFor } from "./registry";

interface StoredConnection {
  enc: string;
  accountId: string;
  accountLabel: string;
  calendarId?: string;
  eventTypeUri?: string;
  eventTypeId?: number;
  subscriptionId?: string | null;
  subscribedAt?: number;
  connectedAt: number;
}

export function integrationDocId(salonId: string, provider: Provider): string {
  return `${salonId}_${provider}`;
}

export function accountIndexId(provider: Provider, accountId: string): string {
  return `${provider}_${sha256Hex(accountId).slice(0, 40)}`;
}

const accounts = () => db.collection("integrationAccounts");

function key(): string {
  const value = secretValue(tokenEncryptionKey);
  if (!value) throw new Error("TOKEN_ENCRYPTION_KEY is not configured");
  return value;
}

function toConnection(provider: Provider, salonId: string, stylistId: string, stored: StoredConnection): Connection {
  const tokens = JSON.parse(decrypt(stored.enc, key())) as TokenSet;
  return {
    provider,
    salonId,
    stylistId,
    tokens,
    accountId: stored.accountId,
    accountLabel: stored.accountLabel,
    calendarId: stored.calendarId,
    eventTypeUri: stored.eventTypeUri,
    eventTypeId: stored.eventTypeId,
  };
}

async function readStored(salonId: string, provider: Provider, stylistId: string): Promise<StoredConnection | null> {
  const snap = await col.integrations().doc(integrationDocId(salonId, provider)).get();
  const stylists = (snap.data()?.stylists ?? {}) as Record<string, StoredConnection | undefined>;
  return stylists[stylistId] ?? null;
}

/** Učitava vezu i po potrebi osvežava access token (i čuva nove, šifrovane tokene). */
export async function loadConnection(salonId: string, provider: Provider, stylistId: string): Promise<Connection | null> {
  const stored = await readStored(salonId, provider, stylistId);
  if (!stored) return null;
  const connection = toConnection(provider, salonId, stylistId, stored);
  if (connection.tokens.expiresAt - Date.now() > 120_000) return connection;
  const refreshed = await providerFor(provider).refreshTokens(connection.tokens);
  await col
    .integrations()
    .doc(integrationDocId(salonId, provider))
    .set({ stylists: { [stylistId]: { enc: encrypt(JSON.stringify(refreshed), key()) } } }, { merge: true });
  return { ...connection, tokens: refreshed };
}

export async function saveConnection(
  salonId: string,
  stylistId: string,
  provider: Provider,
  result: AuthorizationResult,
  subscriptionId: string | null,
): Promise<void> {
  const stored: StoredConnection = {
    enc: encrypt(JSON.stringify(result.tokens), key()),
    accountId: result.accountId,
    accountLabel: result.accountLabel,
    ...(result.calendarId ? { calendarId: result.calendarId } : {}),
    ...(result.eventTypeUri ? { eventTypeUri: result.eventTypeUri } : {}),
    ...(result.eventTypeId !== undefined ? { eventTypeId: result.eventTypeId } : {}),
    subscriptionId,
    subscribedAt: Date.now(),
    connectedAt: Date.now(),
  };
  const batch = db.batch();
  batch.set(
    col.integrations().doc(integrationDocId(salonId, provider)),
    { salonId, provider, stylists: { [stylistId]: stored }, updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
  batch.set(accounts().doc(accountIndexId(provider, result.accountId)), { salonId, stylistId, provider });
  if (provider === "google" && subscriptionId) {
    batch.set(accounts().doc(accountIndexId(provider, subscriptionId.split("|")[0])), { salonId, stylistId, provider });
  }
  batch.update(col.stylists(salonId).doc(stylistId), {
    [`integrations.${provider}`]: {
      connected: true,
      enabled: true,
      accountLabel: result.accountLabel,
      connectedAt: FieldValue.serverTimestamp(),
    },
  });
  await batch.commit();
}

export async function updateSubscription(salonId: string, stylistId: string, provider: Provider, subscriptionId: string): Promise<void> {
  await col
    .integrations()
    .doc(integrationDocId(salonId, provider))
    .set({ stylists: { [stylistId]: { subscriptionId, subscribedAt: Date.now() } } }, { merge: true });
  if (provider === "google") {
    await accounts()
      .doc(accountIndexId(provider, subscriptionId.split("|")[0]))
      .set({ salonId, stylistId, provider });
  }
}

/** Pronalazi frizera po nalogu provajdera (za webhookove). */
export async function findByAccount(provider: Provider, accountId: string): Promise<{ salonId: string; stylistId: string } | null> {
  const snap = await accounts().doc(accountIndexId(provider, accountId)).get();
  const data = snap.data();
  if (!data || typeof data.salonId !== "string" || typeof data.stylistId !== "string") return null;
  return { salonId: data.salonId, stylistId: data.stylistId };
}

export async function removeConnection(salonId: string, stylistId: string, provider: Provider): Promise<void> {
  const stored = await readStored(salonId, provider, stylistId);
  if (stored) {
    try {
      const connection = await loadConnection(salonId, provider, stylistId);
      if (connection && stored.subscriptionId) {
        await providerFor(provider).unsubscribe(connection, stored.subscriptionId);
      }
    } catch (error) {
      logger.warn("Unsubscribe failed while disconnecting", { salonId, stylistId, provider, error: String(error) });
    }
  }
  const batch = db.batch();
  batch.set(
    col.integrations().doc(integrationDocId(salonId, provider)),
    { stylists: { [stylistId]: FieldValue.delete() } },
    { merge: true },
  );
  if (stored) {
    batch.delete(accounts().doc(accountIndexId(provider, stored.accountId)));
    if (provider === "google" && stored.subscriptionId) {
      batch.delete(accounts().doc(accountIndexId(provider, stored.subscriptionId.split("|")[0])));
    }
  }
  batch.update(col.stylists(salonId).doc(stylistId), { [`integrations.${provider}`]: FieldValue.delete() });
  batch.delete(col.busyCache().doc(`${salonId}_${stylistId}`));
  await batch.commit();
}

/** Sve veze (za periodično osvežavanje). */
export async function listConnections(): Promise<{ salonId: string; stylistId: string; provider: Provider; subscribedAt: number; subscriptionId: string | null }[]> {
  const snaps = await col.integrations().get();
  const result: { salonId: string; stylistId: string; provider: Provider; subscribedAt: number; subscriptionId: string | null }[] = [];
  for (const doc of snaps.docs) {
    const data = doc.data();
    const stylists = (data.stylists ?? {}) as Record<string, StoredConnection>;
    for (const [stylistId, stored] of Object.entries(stylists)) {
      result.push({
        salonId: data.salonId as string,
        stylistId,
        provider: data.provider as Provider,
        subscribedAt: stored.subscribedAt ?? 0,
        subscriptionId: stored.subscriptionId ?? null,
      });
    }
  }
  return result;
}
