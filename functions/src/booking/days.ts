import type { DocumentReference, Transaction } from "firebase-admin/firestore";
import { FieldValue } from "firebase-admin/firestore";
import { col, toBusy } from "../common/repo";
import type { BusyInterval } from "../domain/types";
import { pruneBusy } from "../domain/availability";

export function dayRef(salonId: string, stylistId: string, dateKey: string): DocumentReference {
  return col.days(salonId, stylistId).doc(dateKey);
}

/** Čita dokumente dana u transakciji i vraća zauzeća po putanji dokumenta. */
export async function readDaysTx(tx: Transaction, refs: DocumentReference[]): Promise<Map<string, BusyInterval[]>> {
  const unique = [...new Map(refs.map((r) => [r.path, r])).values()];
  const result = new Map<string, BusyInterval[]>();
  if (unique.length === 0) return result;
  const snaps = await tx.getAll(...unique);
  for (const snap of snaps) {
    result.set(snap.ref.path, toBusy(snap.data()?.intervals));
  }
  return result;
}

export function withInterval(busy: BusyInterval[], interval: BusyInterval, nowMs: number): BusyInterval[] {
  return [...pruneBusy(busy, nowMs).filter((b) => b.id !== interval.id), interval].sort((a, b) => a.start - b.start);
}

export function withoutInterval(busy: BusyInterval[], id: string, nowMs: number): BusyInterval[] {
  return pruneBusy(busy, nowMs).filter((b) => b.id !== id);
}

export function writeDay(tx: Transaction, ref: DocumentReference, intervals: BusyInterval[]): void {
  tx.set(
    ref,
    {
      intervals: intervals.map((b) => ({
        id: b.id,
        start: b.start,
        end: b.end,
        kind: b.kind,
        ...(b.expiresAt !== undefined ? { expiresAt: b.expiresAt } : {}),
      })),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}
