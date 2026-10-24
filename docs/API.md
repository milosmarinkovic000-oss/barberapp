# API (Cloud Functions)

Sve funkcije su u regionu `europe-west1`. Aplikacija poziva **callable** funkcije preko Firebase SDK-a
(`FirebaseFunctions.getInstance("europe-west1")`); svaki poziv mora biti od prijavljenog korisnika i, u
produkciji, sa App Check tokenom (Play Integrity).

Konvencije:

- Vremena su milisekunde od epohe (UTC); datumi su `GGGG-MM-DD` po beogradskom vremenu.
- Ulaz se proverava `zod` šemom (`functions/src/common/validation.ts`); nepoznata polja se ignorišu.
- Greška je `HttpsError` čiji `details.code` je jedan od kodova ispod.

## Kodovi grešaka

| `details.code` | HTTPS status | Značenje | Dodatna polja |
|---|---|---|---|
| `SLOT_TAKEN` | `aborted` | slot je upravo zauzet | `date`, `slots` (osveženi slotovi za taj dan) |
| `POLICY_VIOLATION` | `failed-precondition` | pravilo salona ili stanja | `reason`: `CANCEL_DEADLINE`, `OUTSIDE_WINDOW`, `MAX_HOLDS`, `MAX_WAITLIST`, `NOT_ACTIVE`, `NOT_COMPLETED`, `ALREADY_REVIEWED`, `NOT_STARTED`, `HOLD_EXPIRED`, `ALREADY_INVITED`, `NOT_CONNECTED`, `BAD_TRANSITION`, `NO_ACCOUNT`; uz rok i `salonPhone`, `deadline` |
| `NOT_ALLOWED` | `permission-denied` | uloga ne dozvoljava radnju | |
| `VALIDATION` | `invalid-argument` | neispravan ulaz | `issues` (`path`, `message`) |
| `NOT_FOUND` | `not-found` | salon, termin, frizer… ne postoji | |
| `RATE_LIMITED` | `resource-exhausted` | previše zahteva | |
| `INTEGRATION_NOT_CONFIGURED` | `failed-precondition` | server nema ključeve za provajdera | |

## Ograničenja broja zahteva (po korisniku)

| Grupa | Najviše | Funkcije |
|---|---|---|
| dostupnost | 120 / min (u memoriji) | `getAvailability`, `getSalonStats` |
| zaključavanje | 20 / min | `holdSlot`, `releaseHold` |
| izmene | 30 / min | potvrda, otkazivanje, promena, ručni unos, status, blokade, recenzije (odgovor), tim, integracije |
| lista čekanja | 10 / min | `joinWaitlist`, `leaveWaitlist` |
| recenzije | 5 / min | `submitReview` |
| nalog | 10 / min | `syncAccount`, `createSalon`, `deleteAccount` |

## Zakazivanje

### `getAvailability`
Slobodni slotovi.

| Ulaz | Tip |
|---|---|
| `salonId` | string |
| `serviceIds` | string[] (1–5) |
| `stylistId` | string \| null — null = bilo koji frizer koji radi sve usluge |
| `fromDate` | `GGGG-MM-DD` |
| `days` | 1–14 (podrazumevano 14) |
| `excludeAppointmentId` | string, opciono — pri promeni termina, sopstveni termin ne zauzima vreme |

Izlaz: `{ salonId, durationMin, totalPrice, stylistIds, days: [{ date, slots: [{ start, stylistIds }] }] }`.

### `holdSlot`
Zaključava slot na 5 minuta. Ulaz: `salonId`, `serviceIds`, `stylistId` (null = bilo koji), `start`,
`replaceHoldId` (opciono, prethodni hold istog korisnika se oslobađa u istoj transakciji).
Izlaz: `{ appointmentId, stylistId, stylistName, start, end, holdExpiresAt, durationMin, totalPrice }`.
Greške: `SLOT_TAKEN` (sa osveženim slotovima), `POLICY_VIOLATION` (`OUTSIDE_WINDOW`, `MAX_HOLDS`).

### `confirmBooking`
Ulaz: `appointmentId`, `note` (≤ 500), `waitlistId` (opciono). Izlaz: `{ appointment }`.
Greške: `POLICY_VIOLATION` (`HOLD_EXPIRED`), `SLOT_TAKEN`.

### `releaseHold`
Ulaz: `appointmentId`. Izlaz: `{ ok: true }`.

### `cancelAppointment`
Klijent (u roku) ili osoblje salona. Ulaz: `appointmentId`, `reason` (≤ 300, vidi ga klijent kad otkazuje
salon). Izlaz: `{ appointment }`. Posle roka klijent dobija `POLICY_VIOLATION` (`CANCEL_DEADLINE`) sa
`salonPhone` i `deadline`. Oslobođeno vreme obaveštava listu čekanja.

### `rescheduleAppointment`
Atomska promena termina. Ulaz: `appointmentId`, `start`, `stylistId` (null = zadrži/bilo koji).
Izlaz: `{ appointment }`.

### `createManualBooking` (osoblje)
Ulaz: `salonId`, `stylistId`, `serviceIds`, `start`, `customerName`, `customerPhone` (E.164 ili null),
`note`, `walkIn` (dozvoljava vreme van radnog vremena). Izlaz: `{ appointment }`.

### `updateAppointmentStatus` (osoblje)
Ulaz: `appointmentId`, `status` (`confirmed`, `completed`, `no_show`, `canceled_by_salon`), `reason`.
Izlaz: `{ appointment }`.

### `blockTime` (vlasnik, frizer za sebe)
Ulaz: `salonId`, `stylistId`, `start`, `end` (najviše 62 dana), `kind` (`block`, `vacation`, `sick`,
`other`), `reason`. Izlaz: `{ timeOffId, conflicts: string[] }` — postojeći termini koji se preklapaju.

### `removeTimeOff`
Ulaz: `salonId`, `stylistId`, `timeOffId`. Izlaz: `{ ok: true }`; oslobođeni dani obaveštavaju listu
čekanja.

## Lista čekanja i recenzije

| Funkcija | Ulaz | Izlaz |
|---|---|---|
| `joinWaitlist` | `salonId`, `dateKey`, `serviceIds`, `stylistId` | `{ waitlistId }` |
| `leaveWaitlist` | `waitlistId` | `{ ok: true }` |
| `submitReview` | `appointmentId` (završen, sopstveni), `rating` 1–5, `text` (≤ 1000) | `{ reviewId }`; ocena salona i frizera se preračunava |
| `replyToReview` | `salonId`, `reviewId`, `text` (vlasnik) | `{ ok: true }` |

## Nalozi i salon

| Funkcija | Ulaz | Izlaz |
|---|---|---|
| `syncAccount` | `language` (`sr`/`en`), `name` | `{ roles, salonId, stylistId, claimsChanged, isNew }` — pravi profil, prihvata poziv za frizera |
| `createSalon` | `name`, `city`, `address`, `phone`, `lat`, `lng` | `{ salonId }`; pozivalac postaje vlasnik |
| `inviteStylist` | `salonId`, `phone`, `name` (vlasnik) | `{ stylistId, linked }` |
| `setUserRole` | `salonId`, `stylistId`, `role` (`stylist`/`customer`) | uključuje/isključuje pristup frizera |
| `deleteAccount` | `confirm: true` | `{ ok: true }` |
| `getSalonStats` | `salonId`, `period` (`day`/`week`/`month`), `date` | `{ total, completed, noShow, canceled, revenueEstimate, noShowRate, topServices, topStylists, series }` |

## Integracije kalendara

| Funkcija | Ulaz | Izlaz |
|---|---|---|
| `getIntegrationStatus` | — | `{ google, calendly, calcom }` — da li server ima ključeve |
| `startIntegrationOAuth` | `salonId`, `stylistId`, `provider` | `{ url }` za Custom Tab |
| `setIntegrationEnabled` | `salonId`, `stylistId`, `provider`, `enabled` | `{ ok: true }` |
| `disconnectIntegration` | `salonId`, `stylistId`, `provider` | `{ ok: true }` (opoziva tokene i webhook) |

HTTP funkcije (pozivaju ih provajderi, ne aplikacija):

| Putanja | Namena |
|---|---|
| `GET /oauthCallback` | povratak sa OAuth stranice; razmena koda (PKCE), čuvanje šifrovanih tokena, preusmerenje na `termin://integrations?provider=…&status=ok\|error&reason=…` |
| `POST /googleCalendarWebhook` | Google push kanal (provera `X-Goog-Channel-Token`) |
| `POST /calendlyWebhook` | Calendly webhook (potpis `Calendly-Webhook-Signature`) |
| `POST /calcomWebhook` | Cal.com webhook (potpis `x-cal-signature-256`) |

Svaki webhook se obrađuje idempotentno (evidencija u `webhookEvents`).

## Periodične funkcije i okidači

Vidi [ARCHITECTURE.md](ARCHITECTURE.md#periodične-funkcije).

## Parametri i tajne

| Naziv | Vrsta | Opis |
|---|---|---|
| `PUBLIC_FUNCTIONS_URL` | parametar | npr. `https://europe-west1-PROJEKAT.cloudfunctions.net` (OAuth redirect i webhookovi) |
| `APP_DEEP_LINK_BASE` | parametar | `termin://integrations` |
| `GOOGLE_OAUTH_CLIENT_ID`, `CALENDLY_CLIENT_ID`, `CALCOM_CLIENT_ID` | parametar | OAuth klijenti |
| `GOOGLE_OAUTH_CLIENT_SECRET`, `CALENDLY_CLIENT_SECRET`, `CALCOM_CLIENT_SECRET` | tajna | OAuth tajne |
| `CALENDLY_WEBHOOK_SIGNING_KEY`, `CALCOM_WEBHOOK_SECRET` | tajna | provera potpisa webhookova |
| `TOKEN_ENCRYPTION_KEY` | tajna | 32 bajta (base64) za AES-256-GCM šifrovanje tokena |
