import { FieldValue } from "firebase-admin/firestore";
import { db } from "./common/firebase";
import { AppError, notAllowed, notFound } from "./common/errors";
import type { Caller } from "./common/caller";
import { assertOwner } from "./common/caller";
import type { Input } from "./common/validation";
import { col, loadSalon, toAppointment } from "./common/repo";
import { notifyUser } from "./notifications/notify";

/** Novi prosek posle dodavanja jedne ocene (bez ponovnog čitanja svih recenzija). */
export function addRating(current: { avg: number; count: number }, rating: number): { avg: number; count: number } {
  const count = current.count + 1;
  const avg = (current.avg * current.count + rating) / count;
  return { avg: Math.round(avg * 100) / 100, count };
}

export function removeRating(current: { avg: number; count: number }, rating: number): { avg: number; count: number } {
  if (current.count <= 1) return { avg: 0, count: 0 };
  const count = current.count - 1;
  const avg = (current.avg * current.count - rating) / count;
  return { avg: Math.round(Math.max(avg, 0) * 100) / 100, count };
}

function ratingOf(value: unknown): { avg: number; count: number } {
  const v = (value ?? {}) as { avg?: unknown; count?: unknown };
  return {
    avg: typeof v.avg === "number" ? v.avg : 0,
    count: typeof v.count === "number" ? v.count : 0,
  };
}

/** Ime za prikaz uz recenziju: ime i prvo slovo prezimena (najmanje ličnih podataka). */
export function displayName(full: string): string {
  const parts = full.trim().split(/\s+/).filter((p) => p.length > 0);
  if (parts.length === 0) return "";
  if (parts.length === 1) return parts[0];
  return `${parts[0]} ${parts[parts.length - 1][0]}.`;
}

export async function submitReview(caller: Caller, input: Input<"submitReview">): Promise<{ reviewId: string }> {
  const appointmentRef = col.appointments().doc(input.appointmentId);
  const reviewId = input.appointmentId;
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(appointmentRef);
    if (!snap.exists) throw notFound("Appointment");
    const appointment = toAppointment(snap);
    if (appointment.customerUid !== caller.uid) throw notAllowed("Not your appointment");
    if (appointment.status !== "completed") {
      throw new AppError("POLICY_VIOLATION", "Only completed visits can be reviewed", { reason: "NOT_COMPLETED" });
    }
    const salonRef = col.salons().doc(appointment.salonId);
    const stylistRef = col.stylists(appointment.salonId).doc(appointment.stylistId);
    const reviewRef = col.reviews(appointment.salonId).doc(reviewId);
    const [salonSnap, stylistSnap, reviewSnap, userSnap] = await tx.getAll(
      salonRef,
      stylistRef,
      reviewRef,
      col.users().doc(caller.uid),
    );
    if (reviewSnap.exists || appointment.reviewed) {
      throw new AppError("POLICY_VIOLATION", "Already reviewed", { reason: "ALREADY_REVIEWED" });
    }
    const name = typeof userSnap.data()?.name === "string" ? (userSnap.data()?.name as string) : appointment.customerName;
    tx.create(reviewRef, {
      uid: caller.uid,
      authorName: displayName(name),
      appointmentId: appointment.id,
      stylistId: appointment.stylistId,
      stylistName: appointment.stylistName,
      serviceNames: appointment.services.map((s) => s.name),
      rating: input.rating,
      text: input.text,
      reply: null,
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.update(salonRef, { rating: addRating(ratingOf(salonSnap.data()?.rating), input.rating) });
    if (stylistSnap.exists) {
      tx.update(stylistRef, { rating: addRating(ratingOf(stylistSnap.data()?.rating), input.rating) });
    }
    tx.update(appointmentRef, { reviewed: true, updatedAt: FieldValue.serverTimestamp() });
  });
  return { reviewId };
}

export async function replyToReview(caller: Caller, input: Input<"replyToReview">): Promise<void> {
  assertOwner(caller, input.salonId);
  const salon = await loadSalon(input.salonId);
  const ref = col.reviews(input.salonId).doc(input.reviewId);
  const snap = await ref.get();
  if (!snap.exists) throw notFound("Review");
  await ref.update({ reply: { text: input.text, at: FieldValue.serverTimestamp() } });
  const uid: unknown = snap.data()?.uid;
  if (typeof uid === "string") {
    await notifyUser(uid, "review_reply", { salonName: salon.name }, { salonId: salon.id, reviewId: input.reviewId });
  }
}
