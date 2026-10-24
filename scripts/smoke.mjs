#!/usr/bin/env node
// Provera demo okruženja kroz iste API-je koje koristi aplikacija (Auth, Firestore i Functions
// emulatori). Pokreni dok radi `npm run demo`:  npm run smoke
//
// Scenariji (iz „GOTOVO JE KAD“):
//  1. Klijent zakazuje 2 usluge kod „bilo kog frizera“ za sutra; vlasnik i dodeljeni frizer vide termin,
//     a slot nestaje za druge.
//  2. Dva korisnika istovremeno biraju isti slot: uspeva tačno jedan, drugi dobija SLOT_TAKEN i nove slotove.
//  3. Otkazivanje u roku oslobađa slot.
//  4. Blokada vremena odmah uklanja slotove; ručno unet termin se vidi u kalendaru.
//  5. Recenzija završenog termina ažurira ocenu salona.

const PROJECT = "demo-termin";
const REGION = "europe-west1";
const AUTH = "http://127.0.0.1:9099";
const FIRESTORE = `http://127.0.0.1:8080/v1/projects/${PROJECT}/databases/(default)/documents`;
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/${REGION}`;
const ZONE = "Europe/Belgrade";

const CLIENT = "+381600000001";
const OWNER = "+381600000002";
const STYLIST = "+381600000003";
const SECOND_CLIENT = "+381600000077";

let failures = 0;
function check(condition, message) {
  console.log(`${condition ? "✓" : "✗"} ${message}`);
  if (!condition) failures++;
}

async function json(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`${response.status} ${text.slice(0, 200)}`);
  }
}

/** Prijava brojem telefona preko Auth emulatora (kod se čita iz emulatora, kao „Brza demo prijava“). */
async function signIn(phone) {
  const send = await json(
    await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=demo`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ phoneNumber: phone, recaptchaToken: "demo" }),
    }),
  );
  const codes = await json(await fetch(`${AUTH}/emulator/v1/projects/${PROJECT}/verificationCodes`));
  const code = codes.verificationCodes.filter((c) => c.phoneNumber === phone && c.sessionInfo === send.sessionInfo).pop().code;
  const result = await json(
    await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber?key=demo`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionInfo: send.sessionInfo, code }),
    }),
  );
  return result.idToken;
}

/** Callable funkcija (isti protokol kao Firebase SDK). */
async function call(name, data, token) {
  const response = await fetch(`${FUNCTIONS}/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ data }),
  });
  const body = await json(response);
  if (body.error) {
    const error = new Error(`${name}: ${body.error.message}`);
    error.code = body.error.details?.code;
    error.details = body.error.details;
    throw error;
  }
  return body.result;
}

function uidOf(token) {
  return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString()).user_id;
}

/** Prijava sa ulogama: posle usklađivanja naloga token se obnavlja da bi sadržao uloge. */
async function signInWithRoles(phone) {
  const first = await signIn(phone);
  const sync = await call("syncAccount", { language: "sr" }, first);
  return { token: sync.claimsChanged ? await signIn(phone) : first, uid: uidOf(first), ...sync };
}

function value(field) {
  if (field === undefined) return undefined;
  if ("stringValue" in field) return field.stringValue;
  if ("integerValue" in field) return Number(field.integerValue);
  if ("doubleValue" in field) return field.doubleValue;
  if ("booleanValue" in field) return field.booleanValue;
  if ("nullValue" in field) return null;
  if ("timestampValue" in field) return Date.parse(field.timestampValue);
  if ("arrayValue" in field) return (field.arrayValue.values ?? []).map(value);
  if ("mapValue" in field) return Object.fromEntries(Object.entries(field.mapValue.fields ?? {}).map(([k, v]) => [k, value(v)]));
  return undefined;
}

function documentOf(raw) {
  return { id: raw.name.split("/").pop(), ...Object.fromEntries(Object.entries(raw.fields ?? {}).map(([k, v]) => [k, value(v)])) };
}

async function getDoc(path, token) {
  const response = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: `Bearer ${token}` } });
  return { status: response.status, doc: response.ok ? documentOf(await response.json()) : null };
}

async function listDocs(path, token) {
  const response = await fetch(`${FIRESTORE}/${path}?pageSize=200`, { headers: { Authorization: `Bearer ${token}` } });
  const body = await json(response);
  return (body.documents ?? []).map(documentOf);
}

/** Beogradski datum (GGGG-MM-DD) za pomeraj od danas. */
function belgradeDate(offsetDays) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).format(
    new Date(Date.now() + offsetDays * 86_400_000),
  );
  return parts;
}

async function slots(salonId, serviceIds, stylistId, date, token) {
  const result = await call("getAvailability", { salonId, serviceIds, stylistId, fromDate: date, days: 1 }, token);
  return result.days[0].slots.map((s) => s.start);
}

async function main() {
  const owner = await signInWithRoles(OWNER);
  check(owner.roles.includes("owner") && owner.salonId, "vlasnik ima ulogu i salon");
  const stylist = await signInWithRoles(STYLIST);
  check(stylist.roles.includes("stylist") && stylist.salonId === owner.salonId, "frizer pripada istom salonu");
  const client = await signInWithRoles(CLIENT);
  const second = await signInWithRoles(SECOND_CLIENT);
  const salonId = owner.salonId;

  const services = (await listDocs(`salons/${salonId}/services`, client.token)).filter((s) => s.active);
  const stylists = (await listDocs(`salons/${salonId}/stylists`, client.token)).filter((s) => s.active);
  // Dve usluge koje rade bar dva ista frizera (da „bilo koji“ ima izbor).
  let pair = null;
  for (const a of services) {
    for (const b of services) {
      if (a.id >= b.id) continue;
      const shared = a.stylistIds.filter((id) => b.stylistIds.includes(id) && stylists.some((s) => s.id === id));
      if (shared.length >= 2 && a.durationMin + b.durationMin <= 120) pair = [a, b];
    }
  }
  check(pair !== null, "salon ima dve usluge koje radi više frizera");
  if (!pair) throw new Error("seed nema odgovarajuće usluge");
  const serviceIds = pair.map((s) => s.id);
  const tomorrow = belgradeDate(1);

  // 1. Zakazivanje kod „bilo kog frizera“ za sutra.
  const free = await slots(salonId, serviceIds, null, tomorrow, client.token);
  check(free.length > 0, `sutra (${tomorrow}) ima ${free.length} slobodnih termina za „${pair[0].name}“ + „${pair[1].name}“`);
  const start = free[Math.floor(free.length / 2)];

  // 2. Istovremeni izbor istog slota.
  const attempts = await Promise.allSettled([
    call("holdSlot", { salonId, serviceIds, stylistId: null, start }, client.token),
    call("holdSlot", { salonId, serviceIds, stylistId: null, start }, second.token),
  ]);
  const wins = attempts.filter((a) => a.status === "fulfilled");
  const loses = attempts.filter((a) => a.status === "rejected");
  // Sa „bilo kojim frizerom“ oba mogu da uspeju ako su dva frizera slobodna; tada se bira isti
  // frizer za drugi pokušaj da bi se proverilo zaključavanje.
  let hold;
  if (wins.length === 2) {
    const [first, other] = wins.map((w) => w.value);
    await call("releaseHold", { appointmentId: other.appointmentId }, second.token);
    const race = await Promise.allSettled([
      call("holdSlot", { salonId, serviceIds, stylistId: first.stylistId, start, replaceHoldId: first.appointmentId }, client.token),
      call("holdSlot", { salonId, serviceIds, stylistId: first.stylistId, start }, second.token),
    ]);
    const raceWins = race.filter((r) => r.status === "fulfilled");
    const raceLose = race.find((r) => r.status === "rejected");
    check(raceWins.length === 1, "isti frizer i isti slot: uspeva tačno jedan zahtev");
    check(raceLose?.reason.code === "SLOT_TAKEN" && Array.isArray(raceLose.reason.details?.slots), "drugi dobija SLOT_TAKEN i nove slotove");
    hold = raceWins[0].value;
    if (race[0].status === "rejected") {
      // Drugi korisnik je bio brži: klijent ponovo bira drugi slobodan termin.
      await call("releaseHold", { appointmentId: hold.appointmentId }, second.token);
      hold = await call("holdSlot", { salonId, serviceIds, stylistId: null, start }, client.token);
    }
  } else {
    check(wins.length === 1, "istovremeno: uspeva tačno jedan zahtev");
    check(loses[0]?.reason.code === "SLOT_TAKEN" && Array.isArray(loses[0].reason.details?.slots), "drugi dobija SLOT_TAKEN i nove slotove");
    hold = wins[0].value;
    if (attempts[0].status === "rejected") {
      await call("releaseHold", { appointmentId: hold.appointmentId }, second.token);
      hold = await call("holdSlot", { salonId, serviceIds, stylistId: null, start }, client.token);
    }
  }

  const confirmed = await call("confirmBooking", { appointmentId: hold.appointmentId, note: "Smoke test" }, client.token);
  const appointment = confirmed.appointment;
  check(appointment.status === "confirmed", `termin potvrđen kod frizera ${appointment.stylistName}`);
  const mine = await getDoc(`appointments/${appointment.id}`, client.token);
  check(mine.status === 200, "klijent vidi termin (Moji termini)");
  const ownerView = await getDoc(`appointments/${appointment.id}`, owner.token);
  check(ownerView.status === 200, "vlasnik vidi termin u kalendaru salona");
  if (appointment.stylistId === stylist.stylistId) {
    check((await getDoc(`appointments/${appointment.id}`, stylist.token)).status === 200, "dodeljeni frizer vidi termin");
  } else {
    const assigned = stylists.find((s) => s.id === appointment.stylistId);
    console.log(`• dodeljen je frizer ${assigned?.name ?? appointment.stylistId} (demo nalog frizera je drugi frizer; vidljivost proverava test pravila)`);
  }
  const stylistSlots = await slots(salonId, serviceIds, appointment.stylistId, tomorrow, second.token);
  check(!stylistSlots.includes(appointment.start), "taj slot više nije slobodan kod dodeljenog frizera");

  // 3. Otkazivanje u roku oslobađa slot.
  await call("cancelAppointment", { appointmentId: appointment.id }, client.token);
  const afterCancel = await slots(salonId, serviceIds, appointment.stylistId, tomorrow, second.token);
  check(afterCancel.includes(appointment.start), "otkazivanje u roku oslobađa slot");

  // 4. Blokada vremena i ručni unos.
  const blockStylist = appointment.stylistId;
  const blockFrom = afterCancel[0];
  const blocked = await call(
    "blockTime",
    { salonId, stylistId: blockStylist, start: blockFrom, end: blockFrom + 60 * 60_000, kind: "block", reason: "Smoke test" },
    owner.token,
  );
  const afterBlock = await slots(salonId, serviceIds, blockStylist, tomorrow, client.token);
  check(!afterBlock.some((s) => s >= blockFrom && s < blockFrom + 60 * 60_000), "blokirano vreme odmah nestaje iz slobodnih termina");
  await call("removeTimeOff", { salonId, stylistId: blockStylist, timeOffId: blocked.timeOffId }, owner.token);

  const manualStart = (await slots(salonId, [pair[0].id], blockStylist, tomorrow, client.token)).pop();
  const manual = await call(
    "createManualBooking",
    { salonId, stylistId: blockStylist, serviceIds: [pair[0].id], start: manualStart, customerName: "Telefonski klijent", customerPhone: null, note: "", walkIn: false },
    owner.token,
  );
  check((await getDoc(`appointments/${manual.appointment.id}`, owner.token)).status === 200, "ručno unet termin je u kalendaru");
  check(!(await slots(salonId, [pair[0].id], blockStylist, tomorrow, client.token)).includes(manualStart), "ručni termin zauzima slot za klijente");
  await call("cancelAppointment", { appointmentId: manual.appointment.id, reason: "Smoke test" }, owner.token);

  // 5. Recenzija završenog termina.
  const query = await json(
    await fetch(`${FIRESTORE}:runQuery`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${client.token}` },
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "appointments" }],
          where: { fieldFilter: { field: { fieldPath: "customerUid" }, op: "EQUAL", value: { stringValue: client.uid } } },
          limit: 50,
        },
      }),
    }),
  );
  const past = query.filter((r) => r.document).map((r) => documentOf(r.document)).find((a) => a.status === "completed" && !a.reviewed);
  if (past) {
    const before = (await getDoc(`salons/${past.salonId}`, client.token)).doc.rating;
    await call("submitReview", { appointmentId: past.id, rating: 5, text: "Odlično, hvala!" }, client.token);
    const after = (await getDoc(`salons/${past.salonId}`, client.token)).doc.rating;
    check(after.count === before.count + 1, `ocena salona ažurirana (${before.avg} → ${after.avg}, ${after.count} recenzija)`);
  } else {
    console.log("• nema neocenjenog završenog termina (recenzija je već ostavljena u ranijem pokretanju)");
  }

  console.log(failures === 0 ? "\nSve provere su prošle." : `\n${failures} provera nije prošlo.`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error(`✗ ${error.message}`);
  process.exit(1);
});
