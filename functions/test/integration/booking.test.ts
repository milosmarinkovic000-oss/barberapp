import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db } from "../../src/common/firebase";
import { AppError } from "../../src/common/errors";
import { computeAvailability } from "../../src/booking/availabilityService";
import { holdSlot } from "../../src/booking/hold";
import { cancelAppointment, confirmBooking, releaseHold, rescheduleAppointment, updateAppointmentStatus } from "../../src/booking/manage";
import { blockTime, createManualBooking, removeTimeOff } from "../../src/booking/staff";
import { joinWaitlist } from "../../src/waitlist";
import { submitReview, replyToReview } from "../../src/reviews";
import { deleteAccount, inviteStylist, syncAccount } from "../../src/accounts";
import type { Caller } from "../../src/common/caller";
import {
  clearAuth,
  clearFirestore,
  createSalon,
  createUser,
  dateKey,
  localAt,
  requireEmulator,
  staffCaller,
  type SalonFixture,
} from "./fixtures";

let salon: SalonFixture;
let marko: Caller;
let jelena: Caller;
const owner = () => staffCaller(salon.ownerUid, salon.salonId, ["owner"]);

async function slotsFor(day: number, stylistId: string | null, serviceIds: string[], now = Date.now()): Promise<number[]> {
  const result = await computeAvailability({ salonId: salon.salonId, serviceIds, stylistId, fromDate: dateKey(day), days: 1 }, now);
  return result.days[0].slots.map((s) => s.start);
}

beforeAll(() => requireEmulator());

beforeEach(async () => {
  await clearFirestore();
  await clearAuth();
  salon = await createSalon();
  marko = await createUser("marko", "+381600000101", "Marko Marković");
  jelena = await createUser("jelena", "+381600000102", "Jelena Jovanović");
});

describe("holdSlot: istovremeni zahtevi za isti slot", () => {
  it("od 8 istovremenih zahteva kod istog frizera uspeva tačno jedan; ostali dobijaju SLOT_TAKEN i nove slotove", async () => {
    const start = localAt(1, "10:00");
    const callers = await Promise.all(Array.from({ length: 8 }, (_, i) => createUser(`u${i}`, `+3816000002${i}0`, `Korisnik ${i}`)));
    const results = await Promise.allSettled(
      callers.map((c) => holdSlot(c, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start })),
    );
    const ok = results.filter((r) => r.status === "fulfilled");
    const failed = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
    expect(ok).toHaveLength(1);
    expect(failed).toHaveLength(7);
    for (const f of failed) {
      expect(f.reason).toBeInstanceOf(AppError);
      const error = f.reason as AppError;
      expect(error.code).toBe("SLOT_TAKEN");
      const slots = error.details.slots as { start: number }[];
      expect(slots.length).toBeGreaterThan(0);
      expect(slots.map((s) => s.start)).not.toContain(start);
    }
    const holds = await db.collection("appointments").where("status", "==", "held").get();
    expect(holds.size).toBe(1);
  });

  it("„bilo koji frizer“: dva frizera → uspevaju dva od tri zahteva, svaki kod drugog frizera", async () => {
    const start = localAt(1, "11:00");
    const third = await createUser("treci", "+381600000103", "Treći");
    const results = await Promise.allSettled(
      [marko, jelena, third].map((c) => holdSlot(c, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: null, start })),
    );
    const ok = results.filter((r) => r.status === "fulfilled").map((r) => (r as PromiseFulfilledResult<{ stylistId: string }>).value);
    expect(ok).toHaveLength(2);
    expect(new Set(ok.map((h) => h.stylistId))).toEqual(new Set(["ana", "bojan"]));
    const rejected = results.find((r) => r.status === "rejected") as PromiseRejectedResult;
    expect((rejected.reason as AppError).code).toBe("SLOT_TAKEN");
  });

  it("korisnik može imati najviše 2 aktivna hold-a", async () => {
    const base = { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA };
    await holdSlot(marko, { ...base, start: localAt(2, "09:00") });
    await holdSlot(marko, { ...base, start: localAt(2, "10:00") });
    await expect(holdSlot(marko, { ...base, start: localAt(2, "11:00") })).rejects.toMatchObject({
      code: "POLICY_VIOLATION",
      details: { reason: "MAX_HOLDS" },
    });
  });

  it("zamena hold-a (izbor drugog slota) oslobađa prethodni", async () => {
    const base = { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA };
    const first = await holdSlot(marko, { ...base, start: localAt(2, "09:00") });
    await holdSlot(marko, { ...base, start: localAt(2, "09:30"), replaceHoldId: first.appointmentId });
    expect((await db.collection("appointments").doc(first.appointmentId).get()).exists).toBe(false);
    expect(await slotsFor(2, salon.stylistA, [salon.haircut])).toContain(localAt(2, "09:00"));
  });

  it("istekao hold se odmah ignoriše u dostupnosti", async () => {
    const start = localAt(1, "12:00");
    await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start });
    expect(await slotsFor(1, salon.stylistA, [salon.haircut])).not.toContain(start);
    expect(await slotsFor(1, salon.stylistA, [salon.haircut], Date.now() + 6 * 60_000)).toContain(start);
  });
});

describe("tok zakazivanja: 2 usluge kod bilo kog frizera za sutra", () => {
  it("termin je potvrđen, vidi ga salon i frizer, a slot više ne vide drugi klijenti", async () => {
    const services = [salon.haircut, salon.beard];
    const slots = await slotsFor(1, null, services);
    expect(slots.length).toBeGreaterThan(0);
    const start = slots[0];

    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: services, stylistId: null, start });
    expect(hold.durationMin).toBe(60);
    expect(hold.totalPrice).toBe(2300);
    const confirmed = await confirmBooking(marko, { appointmentId: hold.appointmentId, note: "Kraće sa strane" });
    expect(confirmed.status).toBe("confirmed");

    const mine = await db.collection("appointments").where("customerUid", "==", marko.uid).get();
    expect(mine.docs.map((d) => d.get("status") as string)).toEqual(["confirmed"]);
    const salonView = await db.collection("appointments").where("salonId", "==", salon.salonId).get();
    expect(salonView.size).toBe(1);
    const stylistView = await db
      .collection("appointments")
      .where("salonId", "==", salon.salonId)
      .where("stylistId", "==", hold.stylistId)
      .get();
    expect(stylistView.size).toBe(1);

    // Isti frizer više nema taj slot; drugi frizer ga i dalje ima.
    expect(await slotsFor(1, hold.stylistId, services)).not.toContain(start);
    const other = hold.stylistId === "ana" ? "bojan" : "ana";
    expect(await slotsFor(1, other, services)).toContain(start);

    // Kad i drugi frizer zauzme taj slot, „bilo koji“ ga više ne nudi.
    const second = await holdSlot(jelena, { salonId: salon.salonId, serviceIds: services, stylistId: null, start });
    expect(second.stylistId).toBe(other);
    await confirmBooking(jelena, { appointmentId: second.appointmentId, note: "" });
    expect(await slotsFor(1, null, services)).not.toContain(start);
  });

  it("potvrda tuđeg hold-a nije dozvoljena", async () => {
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: null, start: localAt(1, "13:00") });
    await expect(confirmBooking(jelena, { appointmentId: hold.appointmentId, note: "" })).rejects.toMatchObject({ code: "NOT_ALLOWED" });
  });

  it("odustajanje od hold-a odmah oslobađa slot", async () => {
    const start = localAt(1, "14:00");
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start });
    await releaseHold(marko, { appointmentId: hold.appointmentId });
    expect(await slotsFor(1, salon.stylistA, [salon.haircut])).toContain(start);
  });
});

describe("otkazivanje i lista čekanja", () => {
  it("otkazivanje u roku oslobađa slot i obaveštava listu čekanja", async () => {
    const start = localAt(3, "10:00");
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.coloring], stylistId: salon.stylistA, start });
    await confirmBooking(marko, { appointmentId: hold.appointmentId, note: "" });

    // Popuni ostatak dana kod jedine frizerke koja radi bojenje.
    await blockTime(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistA, start: localAt(3, "08:00"), end: localAt(3, "10:00"), kind: "block", reason: "",
    });
    await blockTime(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistA, start: localAt(3, "11:30"), end: localAt(3, "20:00"), kind: "block", reason: "",
    });
    expect(await slotsFor(3, null, [salon.coloring])).toEqual([]);
    await joinWaitlist(jelena, { salonId: salon.salonId, dateKey: dateKey(3), serviceIds: [salon.coloring], stylistId: null });

    await cancelAppointment(marko, { appointmentId: hold.appointmentId, reason: "" });
    const appointment = await db.collection("appointments").doc(hold.appointmentId).get();
    expect(appointment.data()?.status).toBe("canceled_by_customer");
    expect(await slotsFor(3, null, [salon.coloring])).toContain(start);

    const inbox = await db.collection("users").doc(jelena.uid).collection("inbox").get();
    expect(inbox.docs.map((d) => d.get("type") as string)).toContain("waitlist_slot");
    const entry = await db.collection("waitlist").where("uid", "==", jelena.uid).get();
    expect(entry.docs[0].data().status).toBe("notified");
  });

  it("otkazivanje posle roka je blokirano, uz broj telefona salona", async () => {
    const start = Date.now() + 2 * 3_600_000;
    const manual = await createManualBooking(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistB, serviceIds: [salon.haircut], start,
      customerName: "Marko Marković", customerPhone: marko.phone, note: "", walkIn: true,
    });
    expect(manual.customerUid).toBe(marko.uid);
    await expect(cancelAppointment(marko, { appointmentId: manual.id, reason: "" })).rejects.toMatchObject({
      code: "POLICY_VIOLATION",
      details: { reason: "CANCEL_DEADLINE", salonPhone: "+381111234567" },
    });
    await expect(rescheduleAppointment(marko, { appointmentId: manual.id, start: localAt(5, "10:00"), stylistId: null })).rejects.toMatchObject({
      code: "POLICY_VIOLATION",
    });
    // Salon i dalje može da otkaže.
    const canceled = await cancelAppointment(owner(), { appointmentId: manual.id, reason: "" });
    expect(canceled.status).toBe("canceled_by_salon");
    const inbox = await db.collection("users").doc(marko.uid).collection("inbox").get();
    expect(inbox.docs.map((d) => d.get("type") as string)).toContain("booking_canceled_by_salon");
  });

  it("promena termina premešta zauzeće", async () => {
    const start = localAt(4, "10:00");
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start });
    await confirmBooking(marko, { appointmentId: hold.appointmentId, note: "" });
    const moved = await rescheduleAppointment(marko, { appointmentId: hold.appointmentId, start: localAt(4, "15:00"), stylistId: null });
    expect(moved.start).toBe(localAt(4, "15:00"));
    const slots = await slotsFor(4, salon.stylistA, [salon.haircut]);
    expect(slots).toContain(start);
    expect(slots).not.toContain(localAt(4, "15:00"));
  });
});

describe("osoblje: odsustvo, blokada i ručni unos", () => {
  it("odsustvo frizera odmah uklanja slotove, a brisanje ih vraća", async () => {
    expect((await slotsFor(5, salon.stylistB, [salon.haircut])).length).toBeGreaterThan(0);
    const off = await blockTime(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistB, start: localAt(5, "00:00"), end: localAt(7, "00:00"), kind: "vacation", reason: "Godišnji",
    });
    expect(await slotsFor(5, salon.stylistB, [salon.haircut])).toEqual([]);
    expect(await slotsFor(6, salon.stylistB, [salon.haircut])).toEqual([]);
    // „Bilo koji“ i dalje nudi Anu.
    expect((await slotsFor(5, null, [salon.haircut])).length).toBeGreaterThan(0);
    await removeTimeOff(owner(), { salonId: salon.salonId, stylistId: salon.stylistB, timeOffId: off.timeOffId });
    expect((await slotsFor(5, salon.stylistB, [salon.haircut])).length).toBeGreaterThan(0);
  });

  it("blokada vremena prijavljuje termine koji se preklapaju", async () => {
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start: localAt(6, "10:00") });
    await confirmBooking(marko, { appointmentId: hold.appointmentId, note: "" });
    const result = await blockTime(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistA, start: localAt(6, "09:00"), end: localAt(6, "12:00"), kind: "block", reason: "",
    });
    expect(result.conflicts).toEqual([hold.appointmentId]);
  });

  it("frizer blokira samo svoje vreme", async () => {
    const ana = staffCaller(salon.stylistAUid, salon.salonId, ["stylist"], salon.stylistA);
    await blockTime(ana, { salonId: salon.salonId, stylistId: salon.stylistA, start: localAt(2, "12:00"), end: localAt(2, "13:00"), kind: "block", reason: "" });
    await expect(
      blockTime(ana, { salonId: salon.salonId, stylistId: salon.stylistB, start: localAt(2, "12:00"), end: localAt(2, "13:00"), kind: "block", reason: "" }),
    ).rejects.toMatchObject({ code: "NOT_ALLOWED" });
  });

  it("ručno unet termin zauzima slot i ne može da se preklopi", async () => {
    const start = localAt(2, "16:00");
    await createManualBooking(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistB, serviceIds: [salon.haircut], start,
      customerName: "Klijent sa ulice", customerPhone: null, note: "", walkIn: false,
    });
    expect(await slotsFor(2, salon.stylistB, [salon.haircut])).not.toContain(start);
    await expect(
      createManualBooking(owner(), {
        salonId: salon.salonId, stylistId: salon.stylistB, serviceIds: [salon.haircut], start: start + 15 * 60_000,
        customerName: "Drugi", customerPhone: null, note: "", walkIn: false,
      }),
    ).rejects.toMatchObject({ code: "SLOT_TAKEN" });
  });
});

describe("recenzije", () => {
  it("posle završenog termina klijent ostavlja recenziju i ocena salona se ažurira", async () => {
    const start = Date.now() - 2 * 3_600_000;
    const visit = await createManualBooking(owner(), {
      salonId: salon.salonId, stylistId: salon.stylistA, serviceIds: [salon.haircut], start,
      customerName: "Marko Marković", customerPhone: marko.phone, note: "", walkIn: true,
    });
    await expect(submitReview(marko, { appointmentId: visit.id, rating: 5, text: "" })).rejects.toMatchObject({
      code: "POLICY_VIOLATION",
    });
    await updateAppointmentStatus(owner(), { appointmentId: visit.id, status: "completed", reason: "" });
    await submitReview(marko, { appointmentId: visit.id, rating: 4, text: "Odlično šišanje" });

    const salonDoc = await db.collection("salons").doc(salon.salonId).get();
    expect(salonDoc.data()?.rating).toEqual({ avg: 4, count: 1 });
    const stylistDoc = await db.collection("salons").doc(salon.salonId).collection("stylists").doc(salon.stylistA).get();
    expect(stylistDoc.data()?.rating).toEqual({ avg: 4, count: 1 });
    const review = await db.collection("salons").doc(salon.salonId).collection("reviews").doc(visit.id).get();
    expect(review.data()?.authorName).toBe("Marko M.");

    await expect(submitReview(marko, { appointmentId: visit.id, rating: 1, text: "" })).rejects.toMatchObject({
      details: { reason: "ALREADY_REVIEWED" },
    });
    await replyToReview(owner(), { salonId: salon.salonId, reviewId: visit.id, text: "Hvala, Marko!" });
    const replied = await db.collection("salons").doc(salon.salonId).collection("reviews").doc(visit.id).get();
    expect(replied.get("reply.text")).toBe("Hvala, Marko!");
  });

  it("„završeno“ ne može pre početka termina", async () => {
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start: localAt(2, "10:00") });
    await confirmBooking(marko, { appointmentId: hold.appointmentId, note: "" });
    await expect(updateAppointmentStatus(owner(), { appointmentId: hold.appointmentId, status: "completed", reason: "" })).rejects.toMatchObject({
      details: { reason: "NOT_STARTED" },
    });
  });
});

describe("nalozi", () => {
  it("poziv frizera po broju telefona: uloga se dodeljuje pri prijavi", async () => {
    const invite = await inviteStylist(owner(), { salonId: salon.salonId, phone: "+381600000199", name: "Novi Frizer" });
    expect(invite.linked).toBe(false);
    const newbie = await createUser("newbie", "+381600000199", "Novi Frizer");
    const synced = await syncAccount(newbie, {});
    expect(synced.roles).toContain("stylist");
    expect(synced.salonId).toBe(salon.salonId);
    expect(synced.stylistId).toBe(invite.stylistId);
    const stylist = await db.collection("salons").doc(salon.salonId).collection("stylists").doc(invite.stylistId).get();
    expect(stylist.data()?.uid).toBe("newbie");
    const haircut = await db.collection("salons").doc(salon.salonId).collection("services").doc(salon.haircut).get();
    expect(haircut.data()?.stylistIds).toContain(invite.stylistId);
  });

  it("brisanje naloga oslobađa buduće termine i briše lične podatke", async () => {
    const start = localAt(2, "11:00");
    const hold = await holdSlot(marko, { salonId: salon.salonId, serviceIds: [salon.haircut], stylistId: salon.stylistA, start });
    await confirmBooking(marko, { appointmentId: hold.appointmentId, note: "Napomena" });
    await joinWaitlist(marko, { salonId: salon.salonId, dateKey: dateKey(3), serviceIds: [salon.haircut], stylistId: null });

    await deleteAccount(marko);
    const appointment = (await db.collection("appointments").doc(hold.appointmentId).get()).data();
    expect(appointment?.status).toBe("canceled_by_customer");
    expect(appointment?.customerUid).toBeNull();
    expect(appointment?.customerName).toBe("");
    expect(appointment?.customerPhone).toBeNull();
    expect((await db.collection("users").doc(marko.uid).get()).exists).toBe(false);
    expect((await db.collection("waitlist").where("uid", "==", marko.uid).get()).size).toBe(0);
    expect(await slotsFor(2, salon.stylistA, [salon.haircut])).toContain(start);
  });
});
