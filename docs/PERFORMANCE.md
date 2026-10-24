# Performanse

## Izmereno

Merenja su urađena na build mašini (Linux, bez hardverske virtualizacije), na release buildu sa R8
(`./gradlew :app:assembleRelease :app:bundleRelease`).

| Šta | Rezultat | Cilj |
|---|---|---|
| Release APK (univerzalni, sve gustine i ABI) | **4,96 MB** | < 20 MB |
| Android App Bundle (`.aab`) | 11,3 MB (sadrži sve konfiguracije) | — |
| Preuzimanje sa Google Play-a, po uređaju (bundletool `get-size total`) | **4,3–4,5 MB** (arm64-v8a, Android 12+: 4,30–4,33 MB) | < 20 MB |
| Debug APK (bez R8, sa LeakCanary) | 24,9 MB | — |

Veličinu drže malom: R8 sa uklanjanjem nekorišćenih resursa, samo dva jezika (`localeFilters`),
vektorske ikonice, fontovi svedeni na latinične znakove (oko 20–60 KB po rezu), bez nativnih biblioteka
u aplikaciji.

## Nije izmereno na ovoj mašini

**Vreme pokretanja i glatkoća skrolovanja** ne mogu da se izmere bez telefona ili Android emulatora sa
hardverskom virtualizacijom (KVM), koji ovde nisu dostupni. Merenje je spremno i pokreće se jednom
komandom na računaru sa povezanim telefonom ili emulatorom:

```bash
npm run demo                       # u prvom terminalu: emulatori i seed podaci
cd android
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

`StartupBenchmark` meri:

| Test | Metrika | Cilj |
|---|---|---|
| `coldStartupWithProfile` | `timeToInitialDisplayMs` (hladno, sa baseline profilom) | < 1500 ms na telefonu srednje klase |
| `coldStartupWithoutProfile` | isto, bez profila (za poređenje) | — |
| `warmStartup` | `timeToInitialDisplayMs` (toplo) | < 500 ms |
| `salonListScroll` | `frameDurationCpuMs`, `frameOverrunMs` pri listanju salona | P90 ispod budžeta frejma (16,7 ms na 60 Hz) |

Merni build (`benchmarkRelease`) je kao release (R8), ali radi nad lokalnim emulatorima, pa merenje
obuhvata i prijavu demo klijenta i listanje pravih kartica salona iz seed podataka. Rezultati se upisuju u
`android/baselineprofile/build/outputs/connected_android_test_additional_output/`.

Precizan baseline profil za uređaj pravi se sa:

```bash
./gradlew :app:generateBaselineProfile
```

Do tada važi ručni startup profil `android/app/src/main/baseline-prof.txt` (put pokretanja: aplikacija,
navigacija, dizajn sistem, sloj podataka, uvod, prijava, početna, kalendar). Biblioteke (Compose,
Navigation, Firebase) donose sopstvene profile.

## Šta je urađeno za brzinu

Pokretanje:
- SplashScreen API: splash ostaje dok se ne zna stanje prijave (bez praznog ekrana i treptanja).
- Inicijalizacija na `Application` nivou je minimalna: kanali obaveštenja, WorkManager (na zahtev,
  bez `androidx.startup` inicijalizatora), Coil (lenjo). Firebase se inicijalizuje preko svog providera.
- Crashlytics i App Check se ne uključuju u demo režimu.
- Baseline i startup profil; R8 u release-u.

Compose:
- Stabilni modeli (`@Immutable`), `compose-stability.conf` za `java.time` tipove.
- `key` i `contentType` u svim listama; skeleton pri učitavanju.
- Teški poslovi (mapiranje Firestore dokumenata, kompresija slika) van glavne niti.
- Animacije se isključuju kad je sistemska skala animacija 0, a efekti (zamućenje, paralaksa) kad je
  uključena ušteda baterije ili je telefon „low RAM“. Zamućenje samo na Android-u 12+.

Mreža i podaci:
- Firestore offline keš; liste po 20; čitanja uživo samo dok je ekran vidljiv (5 s posle napuštanja se
  prekidaju).
- Ponovni pokušaj sa postepenim odlaganjem za čitanja preko Functions-a.
- Slike: učitavanje u veličini prikaza, keš u memoriji (20 % RAM-a) i na disku (100 MB); umanjene verzije
  za liste i kad je uključena „Ušteda podataka“; upload 1600 px WebP q80 + 480 px umanjena verzija.

Baterija:
- Nema osluškivanja u pozadini; podsetnici preko WorkManager-a.
- Lokacija je približna i traži se samo na zahtev.

Server:
- Dostupnost za jedan dan je jedno čitanje po frizeru (`days` dokument), pa je `getAvailability` brz i
  jeftin; spoljna zauzetost se kešira 10 minuta.
- U emulatoru: `getAvailability` za 14 dana ~0,4–0,5 s, zaključavanje i potvrda ispod 0,3 s.

## Provera kvaliteta

- `./gradlew :app:verifyRoborazziDebug` — unit, UI i 52 snimka ekrana (mali, veliki, preklopni telefon,
  tablet; svetla i tamna tema; slova 200 %; četiri stila).
- StrictMode (debug) beleži čitanja/upise diska i mrežu na glavnoj niti; LeakCanary (debug) curenja
  memorije.
