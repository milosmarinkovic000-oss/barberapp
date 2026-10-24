import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { db } from "../common/firebase";
import { col } from "../common/repo";
import type { BusyInterval, Provider, Stylist } from "../domain/types";
import { addDays, dateKeyOf, startOfLocalDay } from "../domain/time";
import type { BusyRange } from "./provider";
import { isProviderReady, providerFor } from "./registry";
import { loadConnection } from "./store";

/** Zauzetost iz spoljnih kalendara važi 10 minuta. */
export const BUSY_TTL_MS = 10 * 60_000;
/** Keš pokriva danas + 31 dan. */
const CACHE_DAYS = 32;
const FETCH_TIMEOUT_MS = 3500;

interface CachedBusy {
  intervals: { start: number; end: number; provider: Provider }[];
  from: number;
  to: number;
  fetchedAt: number;
}

function cacheRef(salonId: string, stylistId: string) {
  return col.busyCache().doc(`${salonId}_${stylistId}`);
}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timeout")), ms);
    promise.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e: unknown) => {
        clearTimeout(timer);
        reject(e instanceof Error ? e : new Error(String(e)));
      },
    );
  });
}

/** Povlači zauzetost sa svih uključenih provajdera frizera i upisuje keš. */
export async function refreshStylistBusy(salonId: string, stylist: Stylist, zone: string, nowMs = Date.now()): Promise<CachedBusy> {
  const today = dateKeyOf(nowMs, zone);
  const from = startOfLocalDay(today, zone);
  const to = startOfLocalDay(addDays(today, CACHE_DAYS, zone), zone);
  const intervals: CachedBusy["intervals"] = [];
  for (const provider of stylist.enabledProviders) {
    if (!isProviderReady(provider)) continue;
    try {
      const connection = await loadConnection(salonId, provider, stylist.id);
      if (!connection) continue;
      const busy: BusyRange[] = await providerFor(provider).getBusy(connection, from, to);
      intervals.push(...busy.map((b) => ({ start: b.start, end: b.end, provider })));
    } catch (error) {
      logger.warn("External busy fetch failed", { salonId, stylistId: stylist.id, provider, error: String(error) });
    }
  }
  const cached: CachedBusy = { intervals, from, to, fetchedAt: nowMs };
  await cacheRef(salonId, stylist.id).set({ ...cached, updatedAt: FieldValue.serverTimestamp() });
  return cached;
}

/**
 * Spoljna zauzetost po frizeru. Koristi keš mlađi od 10 minuta; ako je keš star, pokušava
 * kratko osvežavanje, a u slučaju greške koristi poslednje poznato stanje.
 */
export async function externalBusy(
  salonId: string,
  stylists: Stylist[],
  zone: string,
  nowMs = Date.now(),
): Promise<Map<string, BusyInterval[]>> {
  const result = new Map<string, BusyInterval[]>();
  const connected = stylists.filter((s) => s.enabledProviders.length > 0);
  if (connected.length === 0) return result;

  const snaps = await db.getAll(...connected.map((s) => cacheRef(salonId, s.id)));
  await Promise.all(
    connected.map(async (stylist, index) => {
      let cached = snaps[index].data() as CachedBusy | undefined;
      const stale = !cached || nowMs - cached.fetchedAt > BUSY_TTL_MS;
      if (stale) {
        try {
          cached = await withTimeout(refreshStylistBusy(salonId, stylist, zone, nowMs), FETCH_TIMEOUT_MS);
        } catch (error) {
          logger.warn("Using stale external busy cache", { salonId, stylistId: stylist.id, error: String(error) });
        }
      }
      const providers = new Set(stylist.enabledProviders);
      result.set(
        stylist.id,
        (cached?.intervals ?? [])
          .filter((i) => providers.has(i.provider))
          .map((i, n) => ({ id: `ext-${i.provider}-${n}`, start: i.start, end: i.end, kind: "external" as const })),
      );
    }),
  );
  return result;
}
