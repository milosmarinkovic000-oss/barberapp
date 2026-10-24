import { logger } from "firebase-functions";
import type { DocumentData } from "firebase-admin/firestore";
import { Timestamp } from "firebase-admin/firestore";
import { refreshNextFree, refreshSalonDerived } from "./salonDerived";

const DAY = 86_400_000;

function millis(value: unknown): number | null {
  return value instanceof Timestamp ? value.toMillis() : null;
}

/** Promena termina utiče na „prvi slobodan termin“ samo ako je u narednih 14 dana. */
export function affectsNextFree(before: DocumentData | undefined, after: DocumentData | undefined, nowMs = Date.now()): boolean {
  const relevant = (d: DocumentData | undefined) => {
    const start = millis(d?.start);
    return start !== null && start >= nowMs - DAY && start <= nowMs + 15 * DAY;
  };
  if (!relevant(before) && !relevant(after)) return false;
  return (
    before?.status !== after?.status ||
    millis(before?.start) !== millis(after?.start) ||
    before?.stylistId !== after?.stylistId
  );
}

export async function onAppointmentChange(before: DocumentData | undefined, after: DocumentData | undefined): Promise<void> {
  if (!affectsNextFree(before, after)) return;
  const salonId = (after?.salonId ?? before?.salonId) as string | undefined;
  if (!salonId) return;
  await refreshNextFree(salonId).catch((error: unknown) => logger.warn("nextFree refresh failed", { salonId, error: String(error) }));
}

export async function onCatalogChange(salonId: string): Promise<void> {
  await refreshSalonDerived(salonId);
  await refreshNextFree(salonId);
}

/** Samo promene polja koja utiču na izvedene vrednosti pokreću ponovno računanje. */
export function salonNeedsRefresh(before: DocumentData | undefined, after: DocumentData | undefined): boolean {
  if (!after) return false;
  const geoChanged =
    JSON.stringify(before?.geo ?? null) !== JSON.stringify(after.geo ?? null) || !after.geohash;
  return (
    geoChanged ||
    before?.name !== after.name ||
    before?.city !== after.city ||
    JSON.stringify(before?.workingHours ?? null) !== JSON.stringify(after.workingHours ?? null) ||
    JSON.stringify(before?.rules ?? null) !== JSON.stringify(after.rules ?? null) ||
    before?.published !== after.published
  );
}
