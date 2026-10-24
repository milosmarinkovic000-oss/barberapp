import { FieldValue } from "firebase-admin/firestore";
import { db } from "./firebase";
import { col } from "./repo";
import { AppError } from "./errors";

export interface Limit {
  /** Najviše poziva u prozoru. */
  max: number;
  windowSec: number;
}

/**
 * Ograničenje broja zahteva po korisniku i funkciji (fiksni prozor, u Firestore-u,
 * pa važi za sve instance funkcije).
 */
export async function enforceRateLimit(uid: string, name: string, limit: Limit, nowMs = Date.now()): Promise<void> {
  const ref = col.rateLimits().doc(`${uid}_${name}`);
  const windowMs = limit.windowSec * 1000;
  const allowed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data();
    const windowStart = typeof data?.windowStart === "number" ? data.windowStart : 0;
    const count = typeof data?.count === "number" ? data.count : 0;
    if (nowMs - windowStart >= windowMs) {
      tx.set(ref, { uid, windowStart: nowMs, count: 1, updatedAt: FieldValue.serverTimestamp() });
      return true;
    }
    if (count >= limit.max) return false;
    tx.update(ref, { count: count + 1, updatedAt: FieldValue.serverTimestamp() });
    return true;
  });
  if (!allowed) {
    throw new AppError("RATE_LIMITED", "Too many requests", { retryAfterSec: limit.windowSec });
  }
}

/** Lagano ograničenje u memoriji instance, za česte čitajuće pozive (dostupnost). */
const buckets = new Map<string, { windowStart: number; count: number }>();

export function enforceLocalRateLimit(uid: string, name: string, limit: Limit, nowMs = Date.now()): void {
  const key = `${uid}_${name}`;
  const bucket = buckets.get(key);
  if (!bucket || nowMs - bucket.windowStart >= limit.windowSec * 1000) {
    buckets.set(key, { windowStart: nowMs, count: 1 });
    if (buckets.size > 10_000) buckets.clear();
    return;
  }
  if (bucket.count >= limit.max) {
    throw new AppError("RATE_LIMITED", "Too many requests", { retryAfterSec: limit.windowSec });
  }
  bucket.count++;
}

export const LIMITS = {
  availability: { max: 120, windowSec: 60 },
  hold: { max: 20, windowSec: 60 },
  mutation: { max: 30, windowSec: 60 },
  waitlist: { max: 10, windowSec: 60 },
  review: { max: 5, windowSec: 60 },
  account: { max: 10, windowSec: 60 },
} satisfies Record<string, Limit>;
