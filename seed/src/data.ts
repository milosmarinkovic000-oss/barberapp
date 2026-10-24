/**
 * Demo podaci: tri izmišljena salona u Beogradu. Nazivi, ljudi i recenzije su izmišljeni;
 * ulice i cene su realni za Beograd.
 */

export type Category = "haircut" | "blowdry" | "coloring" | "highlights" | "beard" | "treatment";
type Hours = Record<"1" | "2" | "3" | "4" | "5" | "6" | "7", { start: string; end: string }[]>;

export interface SeedService {
  id: string;
  category: Category;
  name: string;
  description: string;
  durationMin: number;
  price: number;
  stylists: string[];
}

export interface SeedStylist {
  id: string;
  name: string;
  bio: string;
  specialties: string[];
  /** null = radi po radnom vremenu salona */
  workingHours: Hours | null;
  breaks: Partial<Hours>;
  color: string;
  /** Demo nalog frizera (telefon), ako postoji. */
  accountPhone?: string;
}

export interface SeedReview {
  author: string;
  rating: number;
  text: string;
  daysAgo: number;
  stylist: string;
  reply?: string;
}

export interface SeedSalon {
  id: string;
  name: string;
  slug: string;
  description: string;
  address: string;
  city: string;
  lat: number;
  lng: number;
  phone: string;
  accentColor: string;
  priceLevel: number;
  workingHours: Hours;
  rules: { cancelHours: number; minNoticeMinutes: number; horizonDays: number; slotStepMin: number; bufferMin: number };
  ownerPhone: string;
  ownerName: string;
  stylists: SeedStylist[];
  services: SeedService[];
  reviews: SeedReview[];
}

const lunch = (start: string, end: string, days: string[]): Partial<Hours> =>
  Object.fromEntries(days.map((d) => [d, [{ start, end }]])) as Partial<Hours>;

const WEEKDAYS = ["1", "2", "3", "4", "5"];

export const DEMO_CUSTOMER = { uid: "demo-customer", phone: "+381600000001", name: "Jovana Marković" };
export const DEMO_OWNER = { uid: "demo-owner", phone: "+381600000002", name: "Milica Ristić" };
export const DEMO_STYLIST = { uid: "demo-stylist", phone: "+381600000003", name: "Stefan Ilić" };

/** Ostali (izmišljeni) klijenti čiji termini popunjavaju kalendar. */
export const OTHER_CUSTOMERS = [
  { uid: "seed-c1", name: "Nikola Petrović", phone: "+381641110001" },
  { uid: "seed-c2", name: "Teodora Lukić", phone: "+381641110002" },
  { uid: "seed-c3", name: "Dušan Stanković", phone: "+381641110003" },
  { uid: "seed-c4", name: "Katarina Simić", phone: "+381641110004" },
  { uid: "seed-c5", name: "Milan Popović", phone: "+381641110005" },
  { uid: "seed-c6", name: "Ana Đurić", phone: "+381641110006" },
];

export const SALONS: SeedSalon[] = [
  {
    id: "atelje-lana",
    name: "Atelje Lana",
    slug: "atelje-lana-dorcol",
    description:
      "Mirni atelje na Dorćolu za žensko i muško šišanje, bojenje i negu kose. Radimo sa profesionalnim, veganskim preparatima i uvek odvojimo vreme za konsultaciju pre bojenja.",
    address: "Cara Dušana 42",
    city: "Beograd",
    lat: 44.8236,
    lng: 20.4632,
    phone: "+381112345671",
    accentColor: "#8C6A3F",
    priceLevel: 3,
    workingHours: {
      "1": [{ start: "09:00", end: "21:00" }],
      "2": [{ start: "09:00", end: "21:00" }],
      "3": [{ start: "09:00", end: "21:00" }],
      "4": [{ start: "09:00", end: "21:00" }],
      "5": [{ start: "09:00", end: "21:00" }],
      "6": [{ start: "09:00", end: "18:00" }],
      "7": [{ start: "10:00", end: "16:00" }],
    },
    rules: { cancelHours: 12, minNoticeMinutes: 60, horizonDays: 30, slotStepMin: 15, bufferMin: 10 },
    ownerPhone: DEMO_OWNER.phone,
    ownerName: DEMO_OWNER.name,
    stylists: [
      {
        id: "stefan",
        name: "Stefan Ilić",
        bio: "Muška i ženska kratka šišanja, precizni prelazi i brada. U salonu od otvaranja.",
        specialties: ["Muško šišanje", "Fade", "Brada"],
        workingHours: null,
        breaks: lunch("14:00", "14:30", ["1", "2", "3", "4", "5", "6"]),
        color: "#8C6D46",
        accountPhone: DEMO_STYLIST.phone,
      },
      {
        id: "ivana",
        name: "Ivana Pavlović",
        bio: "Koloristkinja sa deset godina iskustva. Balayage, prirodni prelazi i korekcije boje.",
        specialties: ["Balayage", "Bojenje", "Korekcija boje"],
        workingHours: {
          "1": [],
          "2": [{ start: "10:00", end: "19:00" }],
          "3": [{ start: "10:00", end: "19:00" }],
          "4": [{ start: "12:00", end: "21:00" }],
          "5": [{ start: "12:00", end: "21:00" }],
          "6": [{ start: "09:00", end: "16:00" }],
          "7": [{ start: "10:00", end: "16:00" }],
        },
        breaks: lunch("13:30", "14:00", ["2", "3", "6"]),
        color: "#4F6F64",
      },
      {
        id: "nina",
        name: "Nina Đorđević",
        bio: "Duga kosa, feniranje i svečane frizure. Voli kad klijent izađe sa osmehom.",
        specialties: ["Feniranje", "Svečane frizure", "Tretmani"],
        workingHours: null,
        breaks: lunch("13:00", "13:30", WEEKDAYS),
        color: "#7A4E5A",
      },
      {
        id: "marko",
        name: "Marko Savić",
        bio: "Klasična i moderna muška šišanja, oblikovanje brade toplim peškirom.",
        specialties: ["Muško šišanje", "Brada", "Brijanje"],
        workingHours: {
          "1": [{ start: "09:00", end: "17:00" }],
          "2": [{ start: "09:00", end: "17:00" }],
          "3": [{ start: "09:00", end: "17:00" }],
          "4": [{ start: "09:00", end: "17:00" }],
          "5": [{ start: "09:00", end: "17:00" }],
          "6": [{ start: "09:00", end: "15:00" }],
          "7": [{ start: "10:00", end: "16:00" }],
        },
        breaks: {},
        color: "#4A5F86",
      },
    ],
    services: [
      { id: "zensko-sisanje", category: "haircut", name: "Žensko šišanje", description: "Konsultacija, pranje, šišanje i završno feniranje.", durationMin: 45, price: 2400, stylists: ["stefan", "ivana", "nina"] },
      { id: "musko-sisanje", category: "haircut", name: "Muško šišanje", description: "Šišanje makazama i mašinicom, pranje i stilizovanje.", durationMin: 30, price: 1500, stylists: ["stefan", "marko", "nina"] },
      { id: "feniranje", category: "blowdry", name: "Feniranje", description: "Pranje i feniranje za kratku i srednju dužinu kose.", durationMin: 40, price: 1800, stylists: ["nina", "ivana", "stefan"] },
      { id: "bojenje", category: "coloring", name: "Bojenje cele dužine", description: "Bojenje jednom nijansom, uz negu posle bojenja.", durationMin: 120, price: 5500, stylists: ["ivana", "nina"] },
      { id: "balayage", category: "highlights", name: "Balayage pramenovi", description: "Ručno slikani pramenovi za prirodan prelaz, sa tonerom.", durationMin: 180, price: 9500, stylists: ["ivana"] },
      { id: "brada", category: "beard", name: "Oblikovanje brade", description: "Konture, skraćivanje i ulje za bradu.", durationMin: 20, price: 900, stylists: ["stefan", "marko"] },
      { id: "keratin", category: "treatment", name: "Keratinski tretman", description: "Zaglađivanje i obnova kose; efekat traje do tri meseca.", durationMin: 120, price: 7000, stylists: ["nina"] },
      { id: "hidratacija", category: "treatment", name: "Dubinska hidratacija", description: "Maska i masaža temena za suvu i oštećenu kosu.", durationMin: 30, price: 2200, stylists: ["nina", "ivana", "stefan"] },
    ],
    reviews: [
      { author: "Teodora L.", rating: 5, text: "Ivana je napravila balayage tačno kakav sam htela. Prostor je divan i miran.", daysAgo: 4, stylist: "ivana", reply: "Hvala, Teodora! Vidimo se za osveženje tonera." },
      { author: "Nikola P.", rating: 5, text: "Stefan je precizan i brz, fade savršen. Termin je počeo na vreme.", daysAgo: 9, stylist: "stefan" },
      { author: "Katarina S.", rating: 4, text: "Odlično feniranje, samo je bilo malo gužve na recepciji.", daysAgo: 15, stylist: "nina", reply: "Hvala na iskrenosti, radimo na tome da čekanja ne bude." },
      { author: "Milan P.", rating: 5, text: "Brijanje toplim peškirom kod Marka je obavezno iskustvo.", daysAgo: 21, stylist: "marko" },
      { author: "Ana Đ.", rating: 5, text: "Keratin je ispao fenomenalno, kosa je mekana i sjajna.", daysAgo: 33, stylist: "nina" },
      { author: "Dušan S.", rating: 4, text: "Dobro šišanje i prijatna atmosfera. Cena je malo viša, ali vredi.", daysAgo: 48, stylist: "stefan" },
    ],
  },
  {
    id: "kod-vuka",
    name: "Berbernica Kod Vuka",
    slug: "berbernica-kod-vuka-vracar",
    description:
      "Klasična berbernica na Vračaru: šišanje, brada i brijanje britvom uz dobru kafu. Bez žurbe, ali uvek na vreme.",
    address: "Njegoševa 18",
    city: "Beograd",
    lat: 44.8027,
    lng: 20.4757,
    phone: "+381112345672",
    accentColor: "#3F5E2A",
    priceLevel: 2,
    workingHours: {
      "1": [{ start: "09:00", end: "20:00" }],
      "2": [{ start: "09:00", end: "20:00" }],
      "3": [{ start: "09:00", end: "20:00" }],
      "4": [{ start: "09:00", end: "20:00" }],
      "5": [{ start: "09:00", end: "20:00" }],
      "6": [{ start: "09:00", end: "16:00" }],
      "7": [],
    },
    rules: { cancelHours: 4, minNoticeMinutes: 30, horizonDays: 21, slotStepMin: 15, bufferMin: 5 },
    ownerPhone: "+381600000004",
    ownerName: "Vuk Marinković",
    stylists: [
      {
        id: "vuk",
        name: "Vuk Marinković",
        bio: "Osnivač berbernice. Klasični rezovi, brijanje britvom i oblikovanje brade.",
        specialties: ["Brijanje britvom", "Klasično šišanje", "Brada"],
        workingHours: null,
        breaks: lunch("13:00", "13:30", WEEKDAYS),
        color: "#5E6B3A",
        accountPhone: "+381600000004",
      },
      {
        id: "luka",
        name: "Luka Stojanović",
        bio: "Moderni fade i teksturisani rezovi. Prati trendove i rado savetuje.",
        specialties: ["Fade", "Teksturisani rez", "Dizajn linija"],
        workingHours: {
          "1": [{ start: "12:00", end: "20:00" }],
          "2": [{ start: "12:00", end: "20:00" }],
          "3": [{ start: "12:00", end: "20:00" }],
          "4": [{ start: "12:00", end: "20:00" }],
          "5": [{ start: "12:00", end: "20:00" }],
          "6": [{ start: "09:00", end: "16:00" }],
          "7": [],
        },
        breaks: {},
        color: "#8A5A3C",
      },
      {
        id: "aleksa",
        name: "Aleksa Nikolić",
        bio: "Mašinica, kratke frizure i nega brade. Najmlađi u timu, najbrži u kombinaciji šišanje + brada.",
        specialties: ["Šišanje mašinicom", "Brada", "Nega lica"],
        workingHours: {
          "1": [{ start: "09:00", end: "17:00" }],
          "2": [{ start: "09:00", end: "17:00" }],
          "3": [],
          "4": [{ start: "09:00", end: "17:00" }],
          "5": [{ start: "09:00", end: "17:00" }],
          "6": [{ start: "09:00", end: "16:00" }],
          "7": [],
        },
        breaks: lunch("12:30", "13:00", ["1", "2", "4", "5"]),
        color: "#4A5F86",
      },
    ],
    services: [
      { id: "musko-sisanje", category: "haircut", name: "Muško šišanje", description: "Šišanje makazama ili mašinicom, pranje i stilizovanje.", durationMin: 30, price: 1400, stylists: ["vuk", "luka", "aleksa"] },
      { id: "masinica", category: "haircut", name: "Šišanje mašinicom", description: "Jedna dužina na celoj glavi, uredne konture.", durationMin: 20, price: 1000, stylists: ["vuk", "luka", "aleksa"] },
      { id: "brada", category: "beard", name: "Oblikovanje brade", description: "Skraćivanje, konture britvom i balzam.", durationMin: 20, price: 900, stylists: ["vuk", "luka", "aleksa"] },
      { id: "brijanje", category: "beard", name: "Brijanje toplim peškirom", description: "Tradicionalno brijanje britvom uz topli peškir i negu kože.", durationMin: 30, price: 1300, stylists: ["vuk"] },
      { id: "sisanje-brada", category: "haircut", name: "Šišanje + brada", description: "Kompletan paket: šišanje i oblikovanje brade.", durationMin: 50, price: 2100, stylists: ["vuk", "luka", "aleksa"] },
      { id: "pranje-feniranje", category: "blowdry", name: "Pranje i feniranje", description: "Pranje šamponom po izboru i stilizovanje.", durationMin: 15, price: 700, stylists: ["luka", "aleksa"] },
      { id: "tretman-lica", category: "treatment", name: "Tretman lica za muškarce", description: "Čišćenje, piling i hidratantna maska.", durationMin: 30, price: 1800, stylists: ["aleksa"] },
      { id: "bojenje-brade", category: "coloring", name: "Bojenje brade", description: "Prirodna pokrivenost sedih u bradi.", durationMin: 30, price: 1200, stylists: ["vuk", "luka"] },
    ],
    reviews: [
      { author: "Milan P.", rating: 5, text: "Najbolje brijanje u gradu. Vuk zna posao.", daysAgo: 2, stylist: "vuk", reply: "Hvala, Milane, čast nam je!" },
      { author: "Nikola P.", rating: 4, text: "Luka je napravio odličan fade, samo sam čekao desetak minuta.", daysAgo: 12, stylist: "luka" },
      { author: "Dušan S.", rating: 5, text: "Šišanje i brada za manje od sat vremena, a sve savršeno.", daysAgo: 18, stylist: "aleksa" },
      { author: "Stefan M.", rating: 5, text: "Atmosfera prava stara berbernica, kafa odlična.", daysAgo: 26, stylist: "vuk" },
      { author: "Petar J.", rating: 4, text: "Dobra usluga i fer cene.", daysAgo: 40, stylist: "luka" },
    ],
  },
  {
    id: "studio-aurora",
    name: "Studio Aurora",
    slug: "studio-aurora-novi-beograd",
    description:
      "Moderan studio na Novom Beogradu specijalizovan za pramenove, ombre i svečane frizure. Besplatan parking ispred salona.",
    address: "Jurija Gagarina 151",
    city: "Beograd",
    lat: 44.8044,
    lng: 20.3969,
    phone: "+381112345673",
    accentColor: "#7A1F3D",
    priceLevel: 2,
    workingHours: {
      "1": [{ start: "10:00", end: "20:00" }],
      "2": [{ start: "10:00", end: "20:00" }],
      "3": [{ start: "10:00", end: "20:00" }],
      "4": [{ start: "10:00", end: "20:00" }],
      "5": [{ start: "10:00", end: "20:00" }],
      "6": [{ start: "09:00", end: "17:00" }],
      "7": [{ start: "11:00", end: "15:00" }],
    },
    rules: { cancelHours: 24, minNoticeMinutes: 120, horizonDays: 45, slotStepMin: 15, bufferMin: 15 },
    ownerPhone: "+381600000005",
    ownerName: "Jelena Kovačević",
    stylists: [
      {
        id: "jelena",
        name: "Jelena Kovačević",
        bio: "Vlasnica studija. Pramenovi folijom, ombre i boje koje traju.",
        specialties: ["Pramenovi", "Ombre", "Bojenje"],
        workingHours: null,
        breaks: lunch("14:00", "14:30", WEEKDAYS),
        color: "#7A4E5A",
        accountPhone: "+381600000005",
      },
      {
        id: "sara",
        name: "Sara Milošević",
        bio: "Svečane frizure, punđe i talasi za venčanja i mature.",
        specialties: ["Svečane frizure", "Feniranje", "Šišanje"],
        workingHours: {
          "1": [{ start: "10:00", end: "18:00" }],
          "2": [{ start: "10:00", end: "18:00" }],
          "3": [{ start: "12:00", end: "20:00" }],
          "4": [{ start: "12:00", end: "20:00" }],
          "5": [{ start: "10:00", end: "20:00" }],
          "6": [{ start: "09:00", end: "17:00" }],
          "7": [{ start: "11:00", end: "15:00" }],
        },
        breaks: {},
        color: "#4A5F86",
      },
    ],
    services: [
      { id: "zensko-sisanje", category: "haircut", name: "Žensko šišanje", description: "Šišanje uz pranje i blago feniranje.", durationMin: 45, price: 2000, stylists: ["jelena", "sara"] },
      { id: "sisanje-siski", category: "haircut", name: "Šišanje šiški", description: "Brza korekcija šiški između dva šišanja.", durationMin: 15, price: 600, stylists: ["jelena", "sara"] },
      { id: "feniranje-kratka", category: "blowdry", name: "Feniranje – kratka kosa", description: "Pranje i feniranje kratke kose.", durationMin: 30, price: 1200, stylists: ["sara", "jelena"] },
      { id: "feniranje-duga", category: "blowdry", name: "Feniranje – duga kosa", description: "Pranje i feniranje duge kose, talasi po želji.", durationMin: 45, price: 1800, stylists: ["sara", "jelena"] },
      { id: "izrastak", category: "coloring", name: "Bojenje izrastka", description: "Bojenje izrastka do 3 cm, uz negu.", durationMin: 90, price: 3500, stylists: ["jelena"] },
      { id: "pramenovi", category: "highlights", name: "Pramenovi folijom", description: "Klasični pramenovi folijom, sa tonerom.", durationMin: 150, price: 6500, stylists: ["jelena"] },
      { id: "ombre", category: "highlights", name: "Ombre", description: "Postepen prelaz od tamnog ka svetlom.", durationMin: 180, price: 8000, stylists: ["jelena"] },
      { id: "svecana", category: "blowdry", name: "Svečana frizura", description: "Punđa, pletenice ili talasi za posebne prilike.", durationMin: 60, price: 4000, stylists: ["sara"] },
      { id: "maska", category: "treatment", name: "Tretman maskom", description: "Nega posle bojenja ili za suvu kosu.", durationMin: 20, price: 1000, stylists: ["jelena", "sara"] },
    ],
    reviews: [
      { author: "Ana Đ.", rating: 5, text: "Sara mi je uradila frizuru za venčanje, trajala je ceo dan!", daysAgo: 6, stylist: "sara", reply: "Čestitamo još jednom, Ana!" },
      { author: "Katarina S.", rating: 4, text: "Lepi pramenovi, ali je trajalo duže nego što sam očekivala.", daysAgo: 14, stylist: "jelena" },
      { author: "Teodora L.", rating: 5, text: "Ombre je prirodan i baš kakav sam želela.", daysAgo: 29, stylist: "jelena" },
      { author: "Mina R.", rating: 4, text: "Prijatan studio i parking ispred, preporuka.", daysAgo: 37, stylist: "sara" },
    ],
  },
];
