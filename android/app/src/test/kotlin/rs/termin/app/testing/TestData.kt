package rs.termin.app.testing

import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentSource
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.BookedService
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Rating
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Podaci za testove (isti oblik kao seed, ali mali i predvidljivi). */
object TestData {
    val hours = WeeklyHours(
        DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.associateWith { listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(20, 0))) },
    )

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = date.atTime(hour, minute).atZone(BelgradeZone).toInstant()

    fun salon(id: String = "s1", rules: SalonRules = SalonRules.Default) = Salon(
        id = id,
        name = "Studio Lumière",
        slug = "studio-lumiere",
        description = "Moderni salon u centru.",
        address = "Knez Mihailova 10",
        city = "Beograd",
        location = GeoLocation(44.8176, 20.4569),
        phone = "+381601234567",
        photos = listOf("salons/$id/photos/1.webp"),
        coverPhoto = null,
        accentColor = null,
        rating = Rating(4.8, 120),
        priceLevel = 2,
        workingHours = hours,
        rules = rules,
        ownerUid = "owner",
        published = true,
        categories = setOf(ServiceCategory.Haircut, ServiceCategory.Coloring),
        minPrice = 1500,
        nextFreeAt = null,
    )

    fun service(id: String, stylists: List<String> = listOf("a", "b"), duration: Int = 30, price: Int = 1500) = Service(
        id = id,
        category = ServiceCategory.Haircut,
        name = "Usluga $id",
        description = "",
        durationMin = duration,
        price = price,
        stylistIds = stylists,
        active = true,
        order = 0,
    )

    fun stylist(id: String, name: String = "Frizer $id") = Stylist(
        id = id,
        salonId = "s1",
        name = name,
        photoPath = null,
        bio = "",
        specialties = emptyList(),
        portfolio = emptyList(),
        uid = null,
        workingHours = null,
        breaks = WeeklyHours.Empty,
        active = true,
        order = 0,
        color = null,
        rating = Rating.None,
        nextFreeAt = null,
        integrations = emptyMap(),
    )

    fun appointment(
        id: String = "a1",
        start: Instant,
        durationMin: Int = 30,
        status: AppointmentStatus = AppointmentStatus.Confirmed,
        reviewed: Boolean = false,
        customerUid: String? = "u1",
    ) = Appointment(
        id = id,
        salonId = "s1",
        salonName = "Studio Lumière",
        salonPhone = "+381601234567",
        salonAddress = "Knez Mihailova 10, Beograd",
        stylistId = "a",
        stylistName = "Ana",
        customerUid = customerUid,
        customerName = "Marko Marković",
        customerPhone = "+381600000001",
        services = listOf(BookedService("cut", "Šišanje", 1500, durationMin)),
        start = start,
        end = start.plusSeconds(durationMin * 60L),
        durationMin = durationMin,
        totalPrice = 1500,
        status = status,
        holdExpiresAt = null,
        note = "",
        source = AppointmentSource.App,
        anyStylist = false,
        reviewed = reviewed,
        dateKey = start.atZone(BelgradeZone).toLocalDate().toString(),
    )
}
