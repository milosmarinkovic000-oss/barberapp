package rs.termin.app.core.domain

import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Slot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Deo dana za grupisanje slotova. */
enum class DayPart { Morning, Afternoon, Evening }

/** Jutro do 12:00, popodne do 17:00, veče posle toga (po Beogradu). */
fun dayPartOf(instant: Instant, zone: ZoneId = BelgradeZone): DayPart {
    val hour = instant.atZone(zone).hour
    return when {
        hour < 12 -> DayPart.Morning
        hour < 17 -> DayPart.Afternoon
        else -> DayPart.Evening
    }
}

fun groupByDayPart(slots: List<Slot>, zone: ZoneId = BelgradeZone): Map<DayPart, List<Slot>> =
    DayPart.entries.associateWith { part -> slots.filter { dayPartOf(it.start, zone) == part } }
        .filterValues { it.isNotEmpty() }

/** Prvi slobodan slot u nizu dana (dugme „Prvi slobodan“). */
fun firstFreeSlot(days: List<DaySlots>): Pair<LocalDate, Slot>? =
    days.firstNotNullOfOrNull { day -> day.slots.firstOrNull()?.let { day.date to it } }

/** Zbir trajanja i cene izabranih usluga. */
data class Totals(val durationMin: Int, val price: Int)

fun totalsOf(services: List<Service>): Totals =
    Totals(services.sumOf { it.durationMin }, services.sumOf { it.price })

/** Frizeri koji rade sve izabrane usluge (za „Bilo koji slobodan“). */
fun capableStylistIds(services: List<Service>, activeStylistIds: List<String>): List<String> =
    activeStylistIds.filter { id -> services.all { id in it.stylistIds } }

/** Poslednji trenutak u kom klijent sme sam da otkaže ili promeni termin. */
fun cancellationDeadline(start: Instant, rules: SalonRules): Instant = start.minus(Duration.ofHours(rules.cancelHours.toLong()))

fun canCustomerChange(appointment: Appointment, rules: SalonRules, now: Instant): Boolean =
    appointment.status == AppointmentStatus.Confirmed && !now.isAfter(cancellationDeadline(appointment.start, rules))

/** Može da se oceni: završeno, a još nije ocenjeno. */
fun canReview(appointment: Appointment): Boolean =
    appointment.status == AppointmentStatus.Completed && !appointment.reviewed && appointment.customerUid != null

fun isUpcoming(appointment: Appointment, now: Instant): Boolean =
    appointment.status.isActive && appointment.end.isAfter(now)

/** Rastojanje vazdušnom linijom (haversine) u kilometrima. */
fun distanceKm(a: GeoLocation, b: GeoLocation): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** Datum (po Beogradu) za trenutak. */
fun Instant.belgradeDate(): LocalDate = atZone(BelgradeZone).toLocalDate()
