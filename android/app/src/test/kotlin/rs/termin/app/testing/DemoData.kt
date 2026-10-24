package rs.termin.app.testing

import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BookedService
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Rating
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.WeeklyHours
import java.time.Instant
import java.time.LocalDate

/**
 * Saloni iz demo podataka (`seed/src/data.ts`): Atelje Lana sa uslugama, timom i recenzijama, i još
 * dva salona za liste. Fotografije su putanje iz seed-a (`seed/images`). Koriste ih snimci za sajt.
 */
object DemoData {
    const val LANA = "atelje-lana"
    const val CUSTOMER = "Jovana Marković"
    const val OWNER = "Milica Ristić"

    private val today: LocalDate = TestCommonModule.today

    private fun stylist(id: String, name: String, bio: String, specialties: List<String>, color: Long, rating: Rating) = Stylist(
        id = id,
        salonId = LANA,
        name = name,
        photoPath = "salons/$LANA/stylists/$id/portrait.webp",
        bio = bio,
        specialties = specialties,
        portfolio = (1..2).map { "salons/$LANA/stylists/$id/portfolio-$it.webp" },
        uid = null,
        workingHours = null,
        breaks = WeeklyHours.Empty,
        active = true,
        order = 0,
        color = color,
        rating = rating,
        nextFreeAt = TestData.at(today, 11),
        integrations = emptyMap(),
    )

    val stylists = listOf(
        stylist(
            "stefan", "Stefan Ilić", "Muška i ženska kratka šišanja, precizni prelazi i brada. U salonu od otvaranja.",
            listOf("Muško šišanje", "Fade", "Brada"), 0xFF8C6D46, Rating(4.8, 31),
        ),
        stylist(
            "ivana", "Ivana Pavlović", "Koloristkinja sa deset godina iskustva. Balayage, prirodni prelazi i korekcije boje.",
            listOf("Balayage", "Bojenje", "Korekcija boje"), 0xFF4F6F64, Rating(4.9, 44),
        ),
        stylist(
            "nina", "Nina Đorđević", "Duga kosa, feniranje i svečane frizure. Voli kad klijent izađe sa osmehom.",
            listOf("Feniranje", "Svečane frizure", "Tretmani"), 0xFF7A4E5A, Rating(4.8, 27),
        ),
        stylist(
            "marko", "Marko Savić", "Klasična i moderna muška šišanja, oblikovanje brade toplim peškirom.",
            listOf("Muško šišanje", "Brada", "Brijanje"), 0xFF4A5F86, Rating(4.7, 19),
        ),
    )

    private fun service(id: String, category: ServiceCategory, name: String, description: String, duration: Int, price: Int, stylists: List<String>) =
        Service(id, category, name, description, duration, price, stylists, active = true, order = 0)

    val services = listOf(
        service("zensko-sisanje", ServiceCategory.Haircut, "Žensko šišanje", "Konsultacija, pranje, šišanje i završno feniranje.", 45, 2400, listOf("stefan", "ivana", "nina")),
        service("musko-sisanje", ServiceCategory.Haircut, "Muško šišanje", "Šišanje makazama i mašinicom, pranje i stilizovanje.", 30, 1500, listOf("stefan", "marko", "nina")),
        service("feniranje", ServiceCategory.Blowdry, "Feniranje", "Pranje i feniranje za kratku i srednju dužinu kose.", 40, 1800, listOf("nina", "ivana", "stefan")),
        service("bojenje", ServiceCategory.Coloring, "Bojenje cele dužine", "Bojenje jednom nijansom, uz negu posle bojenja.", 120, 5500, listOf("ivana", "nina")),
        service("balayage", ServiceCategory.Highlights, "Balayage pramenovi", "Ručno slikani pramenovi za prirodan prelaz, sa tonerom.", 180, 9500, listOf("ivana")),
        service("brada", ServiceCategory.Beard, "Oblikovanje brade", "Konture, skraćivanje i ulje za bradu.", 20, 900, listOf("stefan", "marko")),
        service("keratin", ServiceCategory.Treatment, "Keratinski tretman", "Zaglađivanje i obnova kose; efekat traje do tri meseca.", 120, 7000, listOf("nina")),
        service("hidratacija", ServiceCategory.Treatment, "Dubinska hidratacija", "Maska i masaža temena za suvu i oštećenu kosu.", 30, 2200, listOf("nina", "ivana", "stefan")),
    )

    private fun salon(
        id: String,
        name: String,
        description: String,
        address: String,
        location: GeoLocation,
        phone: String,
        accent: Long,
        rating: Rating,
        priceLevel: Int,
        categories: Set<ServiceCategory>,
        minPrice: Int,
        nextFree: Instant,
    ) = Salon(
        id = id,
        name = name,
        slug = id,
        description = description,
        address = address,
        city = "Beograd",
        location = location,
        phone = phone,
        photos = (1..3).map { "salons/$id/photos/gallery-$it.webp" },
        coverPhoto = "salons/$id/photos/cover.webp",
        accentColor = accent,
        rating = rating,
        priceLevel = priceLevel,
        workingHours = TestData.hours,
        rules = SalonRules.Default,
        ownerUid = "owner",
        published = true,
        categories = categories,
        minPrice = minPrice,
        nextFreeAt = nextFree,
    )

    val lana = salon(
        LANA, "Atelje Lana",
        "Mirni atelje na Dorćolu za žensko i muško šišanje, bojenje i negu kose. Radimo sa profesionalnim, veganskim " +
            "preparatima i uvek odvojimo vreme za konsultaciju pre bojenja.",
        "Cara Dušana 42", GeoLocation(44.8236, 20.4632), "+381112345671", 0xFF8C6A3F, Rating(4.7, 6), 3,
        ServiceCategory.entries.toSet(), 900, TestData.at(today, 11),
    )

    val others = listOf(
        salon(
            "kod-vuka", "Berbernica Kod Vuka",
            "Klasična berbernica na Vračaru: šišanje, brada i brijanje britvom uz dobru kafu. Bez žurbe, ali uvek na vreme.",
            "Njegoševa 18", GeoLocation(44.8027, 20.4757), "+381112345672", 0xFF3F5E2A, Rating(4.6, 5), 2,
            setOf(ServiceCategory.Haircut, ServiceCategory.Beard, ServiceCategory.Blowdry, ServiceCategory.Treatment, ServiceCategory.Coloring),
            700, TestData.at(today, 9, 30),
        ),
        salon(
            "studio-aurora", "Studio Aurora",
            "Moderan studio na Novom Beogradu specijalizovan za pramenove, ombre i svečane frizure. Besplatan parking ispred salona.",
            "Jurija Gagarina 151", GeoLocation(44.8044, 20.3969), "+381112345673", 0xFF7A1F3D, Rating(4.5, 4), 2,
            setOf(ServiceCategory.Haircut, ServiceCategory.Blowdry, ServiceCategory.Coloring, ServiceCategory.Highlights, ServiceCategory.Treatment),
            600, TestData.at(today, 12, 15),
        ),
    )

    val reviews = listOf(
        review(
            "r1", "Teodora L.", "Ivana Pavlović", 5, "Ivana je napravila balayage tačno kakav sam htela. Prostor je divan i miran.",
            "Hvala, Teodora! Vidimo se za osveženje tonera.", 4,
        ),
        review("r2", "Nikola P.", "Stefan Ilić", 5, "Stefan je precizan i brz, fade savršen. Termin je počeo na vreme.", null, 9),
        review(
            "r3", "Katarina S.", "Nina Đorđević", 4, "Odlično feniranje, samo je bilo malo gužve na recepciji.",
            "Hvala na iskrenosti, radimo na tome da čekanja ne bude.", 15,
        ),
        review("r4", "Milan P.", "Marko Savić", 5, "Brijanje toplim peškirom kod Marka je obavezno iskustvo.", null, 21),
        review("r5", "Ana Đ.", "Nina Đorđević", 5, "Keratin je ispao fenomenalno, kosa je mekana i sjajna.", null, 33),
    )

    private fun review(id: String, author: String, stylist: String, rating: Int, text: String, reply: String?, daysAgo: Long) =
        Review(id, author, stylist, rating, text, reply, TestData.at(today.minusDays(daysAgo), 12))

    /** Atelje Lana sa svim podacima, ostali saloni u listama. */
    fun applyTo(salons: FakeSalonRepository) {
        salons.salon = lana
        salons.services = services
        salons.stylists = stylists
        salons.others = others
        salons.reviews = reviews
    }

    /** Termin u Atelje Lana. */
    fun appointment(
        id: String,
        day: LocalDate,
        hour: Int,
        minute: Int,
        stylistId: String,
        customer: String,
        serviceIds: List<String>,
        status: AppointmentStatus = AppointmentStatus.Confirmed,
    ): Appointment {
        val chosen = services.filter { it.id in serviceIds }
        val stylist = stylists.first { it.id == stylistId }
        return TestData.appointment(
            id = id,
            start = TestData.at(day, hour, minute),
            durationMin = chosen.sumOf { it.durationMin },
            status = status,
            customerUid = if (customer == CUSTOMER) "u1" else null,
        ).copy(
            salonId = LANA,
            salonName = lana.name,
            salonPhone = lana.phone,
            salonAddress = "${lana.address}, ${lana.city}",
            stylistId = stylist.id,
            stylistName = stylist.name,
            customerName = customer,
            services = chosen.map { BookedService(it.id, it.name, it.price, it.durationMin) },
            totalPrice = chosen.sumOf { it.price },
        )
    }
}
