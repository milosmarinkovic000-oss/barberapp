# Odluke

Spisak odluka donetih tamo gde zadatak nije bio precizan, sa razlogom. Odluke su grupisane po
celinama; svaka stavka kaže **šta** je izabrano i **zašto**.

## Izbor iz odeljka 0

Sve grupe sa jednim izborom bile su prazne, pa važe podrazumevane vrednosti:

| Grupa | Izbor |
|---|---|
| Naziv, paket | „Termin“, `rs.termin.app` (debug: `rs.termin.app.debug`) |
| Grad za demo | Beograd |
| Logo | Monogram „T“ (krug u akcentnoj boji, serif slovo) |
| Stil | A — Luksuzni minimal |
| Paleta | 1 — Šampanj (svetla: pozadina `#FAF8F5`, površina `#FFFFFF`, tekst `#1C1B1A`, sporedni `#6E6A64`, akcenat `#B08D57`; tamna: `#121110` / `#1C1A18` / akcenat `#D4B47A`) |
| Tipografija | Fraunces (naslovi) + Inter (tekst) |
| Zaobljenje | Umereno (12–20 dp) |
| Gustina | Prozračno |
| Animacije | Suptilne |
| Ikonice | Obrisne (Outlined) |
| Kartica salona | Velika fotografija |
| Navigacija | Donja traka sa 4 stavke |
| Tema | Prati telefon |

Sve funkcije iz odeljka 0 su uključene (`Features` u `ThemeConfig.kt`). Isključena funkcija ne
postoji u interfejsu: svaki ulaz (meni, dugme, ekran, tab) proverava zastavicu.

## Proizvod

- **Jezik**: srpski latinica je podrazumevani (`values/`), engleski drugi (`values-en/`). Izbor jezika
  u aplikaciji koristi AppCompat „per-app language“ (Android 13+ i ranije). Jezik se šalje serveru,
  pa i push obaveštenja stižu na izabranom jeziku.
- **Vreme**: sva vremena se čuvaju kao UTC (`Instant`, Firestore `Timestamp`), a računaju i prikazuju po
  `Europe/Belgrade`. Dani promene vremena su pokriveni testovima (i na serveru i u aplikaciji).
- **Cene** su celi dinari (RSD), prikaz „1.200 RSD“.
- **„Bilo koji slobodan“ frizer**: slot je slobodan ako ga ima bar jedan frizer koji radi *sve* izabrane
  usluge; pri zaključavanju se dodeljuje frizer sa najmanje termina tog dana (ravnomerno opterećenje).
- **Zaključavanje slota** traje 5 minuta. Promena termina (reschedule) je atomska na serveru i ne pravi
  hold.
- **Lista čekanja**: kad se termin oslobodi, obaveštavaju se *svi* sa liste za taj dan (i usluge); termin
  dobija ko prvi potvrdi. Ponovno obaveštenje istoj osobi najranije posle 15 minuta.
- **Rok za otkazivanje** važi i za promenu termina. Posle roka aplikacija ne šalje zahtev, već nudi
  „Pozovi salon“ uz objašnjenje.
- **Završeni termini**: osoblje ih označava; potvrđen termin koji niko nije označio 12 h posle kraja
  postaje „završen“, da bi klijent mogao da ostavi recenziju. Molba za recenziju stiže 1 h posle kraja.
- **Ručni unos termina**: „najkraće vreme do termina“ ne važi za osoblje; klijent bez najave može i van
  radnog vremena ako je frizer slobodan. Ako broj telefona pripada korisniku aplikacije, termin se
  vidi i u njegovim „Moji termini“. Za izbor vremena koriste se isti slobodni slotovi kao kod klijenta.
- **Poziv frizera** ide preko broja telefona. Novi frizer se automatski dodaje u sve aktivne usluge
  (inače ne bi mogao da prima termine); vlasnik to menja u izmeni usluge.
- **Usluga važi samo za navedene frizere** (`stylistIds`); prazna lista znači da je niko ne radi.
- **Vlasnik/frizer kao klijent**: prekidač „Prikaz za salon“ u profilu prebacuje na klijentski prikaz.
- **Otvaranje salona**: svaki korisnik može da otvori salon iz profila; postaje vlasnik (custom claim), a
  salon je neobjavljen dok vlasnik ne uključi „Objavi salon“.
- **Brisanje naloga**: otkazuju se budući termini, lični podaci iz prošlih termina se anonimizuju (salon
  zadržava anonimnu statistiku), brišu se profil, fotografija, sanduče, omiljeni i lista čekanja.
  Salon vlasnika se skriva iz pretrage.
- **Deljeni link salona** koristi id salona (`https://<projekat>.web.app/s/{id}`), jer se id ne menja kad
  vlasnik promeni naziv. Firebase Hosting prikazuje stranicu „Otvori u aplikaciji“ za one koji nemaju
  aplikaciju; App Links se potvrđuju preko `assetlinks.json` (`scripts/assetlinks.mjs`).
- **Politika privatnosti** je ekran u aplikaciji (srpski i engleski), dostupan i pre prijave, i ista stranica
  na Firebase Hosting-u (`/privatnost`), uz stranicu za zahtev za brisanje naloga (`/brisanje-naloga`) koju
  Google Play traži. Za kontakt se upućuje na e-poštu sa stranice aplikacije u Google Play-u (tamo je
  obavezna).

## Backend

- **Region** `europe-west1` (Belgija): najbliži region sa punom podrškom za Cloud Functions 2. generacije,
  Firestore i Secret Manager. Firestore baza u produkciji: `eur3` ili `europe-west1`.
- **Node 22**, `firebase-functions` 7, `firebase-admin` 14, `zod` 4 za proveru ulaza, `luxon` za vremenske
  zone.
- **Model zauzetosti**: za svakog frizera i dan postoji dokument `salons/{s}/stylists/{f}/days/{GGGG-MM-DD}`
  sa intervalima (termin, hold, odsustvo, spoljni kalendar). Zaključavanje, potvrda, otkazivanje i
  blokade menjaju te dokumente u Firestore transakciji, pa dva istovremena zahteva za isti slot ne mogu
  oba da uspeju. Dostupnost se računa iz istih dokumenata (jedno čitanje po frizeru i danu).
- **Pauza između termina** (`bufferMin`) dodaje se posle svakog termina.
- **Greške** se vraćaju kao `HttpsError` sa kodom u `details.code` (`SLOT_TAKEN`, `POLICY_VIOLATION`,
  `NOT_ALLOWED`, `VALIDATION`, `NOT_FOUND`, `RATE_LIMITED`, `INTEGRATION_NOT_CONFIGURED`); aplikacija ih
  prevodi u poruke. `SLOT_TAKEN` nosi osvežene slotove za taj dan.
- **Ograničenje zahteva**: brojači u Firestore-u po korisniku i funkciji za izmene; u memoriji instance za
  česta čitanja (dostupnost, statistika).
- **Push** preko Firebase Installation ID-ja: FCM 25.1+ zastareva `getToken/onNewToken`, pa se uređaj
  registruje preko `onRegistered` + `FirebaseInstallations`, a server šalje na `fids`.
- **Podsetnici** postoje i na serveru (push 24 h i 2 h pre) i lokalno (WorkManager). Ključ podsetnika
  sprečava da se isti podsetnik prikaže dvaput.
- **Integracije kalendara**: OAuth 2.0 sa PKCE ide preko Cloud Functions; tokeni se čuvaju šifrovani
  (AES-256-GCM, ključ `TOKEN_ENCRYPTION_KEY`) u kolekciji koju klijenti ne mogu da čitaju. Zauzetost iz
  spoljnog kalendara se kešira 10 minuta i osvežava periodično i na webhook. Webhookovi se proveravaju
  potpisom (Calendly `t=,v1=` HMAC, Cal.com `x-cal-signature-256`, Google kanal sa HMAC tokenom) i
  obrađuju idempotentno.
- **Demo režim** nema podešene integracije; tajne su u `functions/.secret.local` označene kao
  `not-configured` (emulator praznu vrednost tretira kao nepostojeću i pokušava Secret Manager).
- **Pretraga** salona po nazivu, adresi i uslugama koristi niz normalizovanih ključnih reči (bez
  dijakritika, prefiksi) i `array-contains`; „u blizini“ koristi geohash opsege (GeoFire).

## Android

- **Moduli**: jedan `:app` modul sa paketima po celinama; dodatni `:baselineprofile` je *test* modul koji
  Macrobenchmark i generisanje baseline profila zahtevaju (ne sadrži kod aplikacije).
- **SDK**: `minSdk 26`; `targetSdk 36` (zahtev Google Play-a); `compileSdk 37` jer ga traže trenutne
  AndroidX biblioteke. Lint provera `OldTargetApi` je zato isključena uz obrazloženje u `lint.xml`;
  prelazak na target 37 radi se posebno, uz proveru promena ponašanja.
- **Kotlin** je ugrađen u AGP 9 (bez zasebnog Kotlin Gradle plugina); JVM 17; sva upozorenja kompajlera i
  lint-a su greške.
- **Navigacija**: tipizovane rute; isti graf za klijenta i osoblje, sa različitim početnim ekranom.
  Link `termin://appointment/{id}` vodi u detalj koji odgovara prikazu (klijent ili salon).
- **Prilagodljiv raspored**: od 600 dp širine bočna traka (navigation rail), od 840 dp lista + detalji
  jedno pored drugog (pretraga, moji termini, termini salona). Dnevni kalendar ima kolonu po frizeru;
  kad kolone ne staju, kalendar se lista vodoravno. Nedeljni prikaz je spisak po danima, čitljiv i na
  telefonu.
- **„Pregled stila“** je u glavnom izvornom kodu, ali je ruta registrovana samo kad je `BuildConfig.DEBUG`;
  R8 ga uklanja iz release-a. Zasebni `src/debug`/`src/release` skupovi nisu korišćeni jer merni buildovi
  (`benchmarkRelease`, `nonMinifiedRelease`) ne vide `src/release`.
- **Izbor sa „Pregleda stila“** čuva se u DataStore-u samo u debug buildu; release uvek koristi
  `ThemeConfig.Default`.
- **Akcentne nijanse u aplikaciji**: 5 nijansi izvedenih iz akcenta palete (HSL svetlina ±), svaka
  provučena kroz proveru kontrasta.
- **Boje salona**: 8 ponuđenih boja; u tamnoj temi boja se posvetljuje da ostane čitljiva.
- **Slike**: Coil 3 sa kešom u memoriji (20 % RAM-a) i na disku (100 MB); upload se smanjuje na 1600 px
  WebP kvaliteta 80 uz umanjenu verziju od 480 px za liste. Kad je uključena „Ušteda podataka“, svuda se
  koriste umanjene verzije.
- **Lokacija** je približna (coarse) i traži se samo za „U blizini“, pretragu po rastojanju i „Koristi moju
  lokaciju“ pri unosu salona.
- **Dozvola za obaveštenja** se traži na ekranu uspeha posle prvog zakazivanja, uz objašnjenje.
- **App Check**: Play Integrity u release-u; debug build nad pravim projektom
  (`-Ptermin.useEmulators=false`) koristi debug provider (token iz logcat-a se dodaje u konzoli). Debug
  provider je u kodu samo pod `BuildConfig.DEBUG`, pa ga R8 uklanja iz release-a.
- **HTTP ka emulatorima**: debug i merni buildovi dozvoljavaju nešifrovan saobraćaj (emulatori na računaru
  rade preko HTTP-a, a IP adresa računara u lokalnoj mreži za fizički telefon nije unapred poznata).
  Release build koristi glavnu konfiguraciju, koja dozvoljava samo HTTPS.
- **Merni buildovi** (Macrobenchmark, baseline profil) rade nad lokalnim emulatorima sa seed podacima,
  da bi merenje obuhvatilo prijavu i listu salona bez pravog naloga.
- **Baseline profil**: u repozitorijumu je ručni startup profil (`baseline-prof.txt`) dok se na uređaju
  ne generiše precizniji (`./gradlew :app:generateBaselineProfile`).
- **Lint**: isključene su samo provere koje zavise od spoljnog sveta (nove verzije biblioteka) i dve
  provere koje ne važe za ovaj projekat (`MissingFirebaseInstanceTokenRefresh` — koristi se FID;
  `OldTargetApi` — vidi SDK). Sve ostalo je greška.

## Testovi

- **Robolectric** koristi običnu `Application` klasu (bez Hilt-a i Firebase-a); ViewModel-i se u testovima
  prave ručno sa lažnim repozitorijumima u memoriji (`testing/Fakes.kt`), koji simuliraju server
  (zauzeti slotovi, `SLOT_TAKEN` sa osveženim slotovima). MockK se koristi za use case-ove, Turbine za
  tokove stanja.
- **Snimci ekrana**: Roborazzi (Robolectric „native graphics“ crta pravi Compose sa pravim fontovima).
  Snimci su upola smanjeni da repozitorijum ostane mali; matrica: mali, veliki i preklopni telefon i
  tablet; svetla i tamna tema; slova 200 %; stil A i stilovi B, D, E.
- **Nazivi testova** su ASCII (bez dijakritika), jer JVM bez UTF-8 lokala ne može da upiše klase sa tim
  imenima.
- **Smoke test** (`npm run smoke`) proverava scenarije iz „GOTOVO JE KAD“ nad pokrenutim demo okruženjem,
  kroz iste API-je koje koristi aplikacija.
