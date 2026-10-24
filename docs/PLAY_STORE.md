# Google Play

Podaci za stranicu aplikacije i obrasce u Play Console-u.

## Osnovno

| Polje | Vrednost |
|---|---|
| Naziv aplikacije (≤ 30) | Termin — zakazivanje u salonu |
| Paket | `rs.termin.app` |
| Kategorija | Lepota (Beauty) |
| Oznake | frizer, salon, zakazivanje, šišanje, barber |
| Podrazumevani jezik | srpski (latinica), `sr-Latn`; dodatni: engleski |
| Zemlje | Srbija (i druge po želji) |
| Cena | besplatno, bez reklama i kupovina u aplikaciji |
| E-pošta podrške | adresa tima (obavezna; politika privatnosti i stranica za brisanje naloga upućuju na nju) |
| Politika privatnosti | `https://<projekat>.web.app/privatnost` ili na GitHub Pages `https://<korisnik>.github.io/<repozitorijum>/privatnost.html` — isti tekst kao ekran u aplikaciji (Profil → Politika privatnosti) |
| Brisanje naloga (web) | `https://<projekat>.web.app/brisanje-naloga` ili `https://<korisnik>.github.io/<repozitorijum>/brisanje-naloga.html` |

## Kratak opis (≤ 80 znakova)

> Pronađi frizerski salon i zakaži termin za par dodira — bez poziva i čekanja.

## Dug opis (≤ 4000 znakova)

> **Termin** je najlakši način da zakažeš šišanje, feniranje, bojenje ili tretman u salonu u svom gradu.
>
> **Pronađi salon**
> • Saloni u blizini, sa rastojanjem i prvim slobodnim terminom
> • Pretraga po nazivu i usluzi, filteri za cenu, ocenu, rastojanje i „slobodno danas“
> • Prikaz kao lista ili na mapi
> • Fotografije, ocene i recenzije drugih klijenata, cenovnik i radno vreme
>
> **Zakaži za minut**
> • Izaberi jednu ili više usluga — odmah vidiš ukupno trajanje i cenu
> • Izaberi frizera ili „bilo koji slobodan“
> • Traka sa 14 dana, slobodni termini po delovima dana i dugme „Prvi slobodan“
> • Izabrani termin se čuva za tebe 5 minuta dok ne potvrdiš
> • Napomena za frizera, „Dodaj u kalendar“ i „Put do salona“
>
> **Bez propuštenih termina**
> • Podsetnik dan ranije i 2 sata pre termina
> • Promena ili otkazivanje u roku koji salon odredi, u jednom dodiru
> • Lista čekanja: javimo ti čim se oslobodi termin za dan koji želiš
> • „Zakaži ponovo“ za omiljenu uslugu
>
> **Za salone**
> • Kalendar po danima i nedeljama, sa kolonom za svakog frizera
> • Ručni unos termina zakazanih telefonom i klijenata bez najave
> • Blokiranje vremena, pauze i godišnji odmori
> • Usluge, cene, tim i radno vreme na jednom mestu
> • Povezivanje sa Google Calendar-om, Calendly-jem i Cal.com-om
> • Statistika: broj termina, procena prihoda, nedolasci, najtraženije usluge
> • Odgovori na recenzije i sopstvena boja salona
>
> **Za frizere**
> • Moj raspored, detalji termina i klijenta, ranije posete
> • Označavanje „završeno“ i „nije došao“, unos odsustva
>
> Aplikacija je na srpskom i engleskom, prati svetlu i tamnu temu telefona, radi i sa uvećanim tekstom i
> čitačem ekrana TalkBack.

## Grafika

- Ikonica 512 × 512: monogram „T“ (izvor: `android/tools/generate_icons.py`).
- Snimci ekrana telefona i tableta: polazna osnova su snimci iz `android/app/src/test/screenshots/`
  (u punoj veličini se prave sa `./gradlew recordRoborazziDebug` bez umanjenja, ili na uređaju).
- Grafika za istaknuto (1024 × 500): monogram i slogan „Zakaži termin za par dodira“ na pozadini palete.

## Ocena sadržaja (IARC)

Upitnik: aplikacija za usluge, bez nasilja, seksualnog sadržaja, kockanja i nepristojnog jezika.
Korisnici mogu da pišu recenzije (korisnički sadržaj, moderira salon kroz odgovore; prijava zloupotrebe
preko e-pošte podrške). Deljenje lokacije sa drugim korisnicima: ne. Očekivana ocena: PEGI 3 / „Svi“.

## Ciljna grupa

18+ (korisnici zakazuju usluge i ostavljaju broj telefona). Aplikacija nije namenjena deci.

## Data Safety (bezbednost podataka)

**Da li aplikacija prikuplja ili deli podatke?** Prikuplja: da. Deli sa trećim stranama: ne (podaci koje
korisnik namerno šalje salonu kod kog zakazuje su radnja korisnika, a Google Firebase je obrađivač).

**Šifrovanje u prenosu:** da (HTTPS/TLS). **Brisanje podataka:** korisnik može da zatraži brisanje (u
aplikaciji i preko web stranice).

| Vrsta podatka | Prikuplja se | Obavezno | Svrha | Napomena |
|---|---|---|---|---|
| Lične informacije → Ime | da | da | funkcionalnost aplikacije, upravljanje nalogom | prikazuje se salonu kod kog korisnik zakazuje |
| Lične informacije → Broj telefona | da | da (prijava telefonom) ili ne (prijava Google nalogom) | funkcionalnost, upravljanje nalogom | prijava; salon vidi broj za zakazani termin |
| Lične informacije → E-pošta | da | ne (samo uz Google prijavu) | upravljanje nalogom | |
| Fotografije i video → Fotografije | da | ne | funkcionalnost aplikacije | fotografija profila; za osoblje fotografije salona i portfolio |
| Lokacija → Približna lokacija | da | ne | funkcionalnost aplikacije | samo dok je ekran otvoren, za „U blizini“ i rastojanje; obrađuje se trenutno, ne čuva se |
| Aktivnost u aplikaciji → Druge radnje (termini, lista čekanja, omiljeni) | da | da | funkcionalnost aplikacije | |
| Aktivnost u aplikaciji → Drugi sadržaj korisnika (recenzije) | da | ne | funkcionalnost aplikacije | javno, sa imenom i inicijalom prezimena |
| ID-ovi uređaja ili drugi ID-ovi (Firebase Installation ID) | da | da | funkcionalnost (push obaveštenja) | |
| Informacije o aplikaciji i performansama → Izveštaji o padu, dijagnostika | da | da | analitika (stabilnost) | Crashlytics, bez sadržaja termina |

Ne prikupljaju se: finansijski podaci, zdravlje, poruke, kontakti, kalendar korisnika (klijenta), audio,
datoteke, precizna lokacija, istorija pretraživanja na webu.

Za osoblje salona koje poveže kalendar: OAuth tokeni za Google Calendar, Calendly ili Cal.com čuvaju se
šifrovani na serveru i koriste samo za usklađivanje zauzetosti i upis termina (pristup kalendaru se
navodi u Google OAuth verifikaciji, ne u Data Safety obrascu aplikacije, jer se obrađuje na serveru).

## Dozvole i deklaracije

| Dozvola | Zašto |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | rad sa serverom, prikaz „bez interneta“ |
| `ACCESS_COARSE_LOCATION` | „U blizini“ i rastojanje (traži se tek na dodir) |
| `POST_NOTIFICATIONS` | potvrde i podsetnici (traži se posle prvog zakazivanja) |

Nema pristupa pozadinskoj lokaciji, kontaktima, SMS-u ni kalendaru telefona („Dodaj u kalendar“ koristi
sistemski ekran kalendara).

## Zahtevi Google Play-a

- Target API: 36 (`targetSdk 36`), Android App Bundle, potpisivanje preko Play App Signing.
- Brisanje naloga: u aplikaciji (Profil → Obriši nalog) i web link.
- App Check (Play Integrity) uključen za Cloud Functions u produkciji.
