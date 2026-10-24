package rs.termin.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Vremenska zona tržišta. Sva vremena se čuvaju u UTC ([Instant]), a prikazuju po Beogradu. */
val BelgradeZone: ZoneId = ZoneId.of("Europe/Belgrade")

enum class ServiceCategory(val key: String) {
    Haircut("haircut"),
    Blowdry("blowdry"),
    Coloring("coloring"),
    Highlights("highlights"),
    Beard("beard"),
    Treatment("treatment"),
    ;

    companion object {
        fun from(key: String?): ServiceCategory = entries.firstOrNull { it.key == key } ?: Haircut
    }
}

enum class Role { Customer, Owner, Stylist }

@Immutable
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    override fun toString(): String = "%02d:%02d–%02d:%02d".format(start.hour, start.minute, end.hour, end.minute)
}

/** Radno vreme po danu u nedelji; prazna lista = ne radi. */
@Immutable
data class WeeklyHours(val days: Map<DayOfWeek, List<TimeRange>>) {
    fun on(day: DayOfWeek): List<TimeRange> = days[day].orEmpty()

    companion object {
        val Empty = WeeklyHours(emptyMap())
    }
}

@Immutable
data class SalonRules(
    val cancelHours: Int,
    val minNoticeMinutes: Int,
    val horizonDays: Int,
    val slotStepMin: Int,
    val bufferMin: Int,
) {
    companion object {
        val Default = SalonRules(cancelHours = 12, minNoticeMinutes = 60, horizonDays = 30, slotStepMin = 15, bufferMin = 10)
    }
}

@Immutable
data class Rating(val average: Double, val count: Int) {
    companion object {
        val None = Rating(0.0, 0)
    }
}

@Immutable
data class GeoLocation(val latitude: Double, val longitude: Double)

@Immutable
data class Salon(
    val id: String,
    val name: String,
    val slug: String,
    val description: String,
    val address: String,
    val city: String,
    val location: GeoLocation?,
    val phone: String,
    /** Putanje fotografija u Cloud Storage-u. */
    val photos: List<String>,
    val coverPhoto: String?,
    /** Akcentna boja salona (0xAARRGGBB), ako je vlasnik izabrao. */
    val accentColor: Long?,
    val rating: Rating,
    val priceLevel: Int,
    val workingHours: WeeklyHours,
    val rules: SalonRules,
    val ownerUid: String?,
    val published: Boolean,
    val categories: Set<ServiceCategory>,
    val minPrice: Int?,
    val nextFreeAt: Instant?,
)

@Immutable
data class Service(
    val id: String,
    val category: ServiceCategory,
    val name: String,
    val description: String,
    val durationMin: Int,
    val price: Int,
    val stylistIds: List<String>,
    val active: Boolean,
    val order: Int,
)

enum class Provider(val key: String) {
    Google("google"),
    Calendly("calendly"),
    Calcom("calcom"),
    ;

    companion object {
        fun from(key: String): Provider? = entries.firstOrNull { it.key == key }
    }
}

@Immutable
data class IntegrationState(val connected: Boolean, val enabled: Boolean, val accountLabel: String)

@Immutable
data class Stylist(
    val id: String,
    val salonId: String,
    val name: String,
    val photoPath: String?,
    val bio: String,
    val specialties: List<String>,
    val portfolio: List<String>,
    val uid: String?,
    /** null = radi po radnom vremenu salona. */
    val workingHours: WeeklyHours?,
    val breaks: WeeklyHours,
    val active: Boolean,
    val order: Int,
    val color: Long?,
    val rating: Rating,
    val nextFreeAt: Instant?,
    val integrations: Map<Provider, IntegrationState>,
)

@Immutable
data class Review(
    val id: String,
    val authorName: String,
    val stylistName: String,
    val rating: Int,
    val text: String,
    val reply: String?,
    val createdAt: Instant,
)

enum class AppointmentStatus(val key: String) {
    Held("held"),
    Confirmed("confirmed"),
    CanceledByCustomer("canceled_by_customer"),
    CanceledBySalon("canceled_by_salon"),
    Completed("completed"),
    NoShow("no_show"),
    ;

    val isCanceled: Boolean get() = this == CanceledByCustomer || this == CanceledBySalon
    val isActive: Boolean get() = this == Held || this == Confirmed

    companion object {
        fun from(key: String?): AppointmentStatus = entries.firstOrNull { it.key == key } ?: Confirmed
    }
}

enum class AppointmentSource(val key: String) {
    App("app"), Manual("manual"), Calendly("calendly"), Google("google"), Calcom("calcom");

    companion object {
        fun from(key: String?): AppointmentSource = entries.firstOrNull { it.key == key } ?: App
    }
}

@Immutable
data class BookedService(val id: String, val name: String, val price: Int, val durationMin: Int)

@Immutable
data class Appointment(
    val id: String,
    val salonId: String,
    val salonName: String,
    val salonPhone: String,
    val salonAddress: String,
    val stylistId: String,
    val stylistName: String,
    val customerUid: String?,
    val customerName: String,
    val customerPhone: String?,
    val services: List<BookedService>,
    val start: Instant,
    val end: Instant,
    val durationMin: Int,
    val totalPrice: Int,
    val status: AppointmentStatus,
    val holdExpiresAt: Instant?,
    val note: String,
    val source: AppointmentSource,
    val anyStylist: Boolean,
    val reviewed: Boolean,
    val dateKey: String,
)

@Immutable
data class Slot(val start: Instant, val stylistIds: List<String>)

@Immutable
data class DaySlots(val date: LocalDate, val slots: List<Slot>)

@Immutable
data class Availability(
    val salonId: String,
    val durationMin: Int,
    val totalPrice: Int,
    val stylistIds: List<String>,
    val days: List<DaySlots>,
)

@Immutable
data class Hold(
    val appointmentId: String,
    val stylistId: String,
    val stylistName: String,
    val start: Instant,
    val end: Instant,
    val expiresAt: Instant,
    val durationMin: Int,
    val totalPrice: Int,
)

@Immutable
data class UserProfile(
    val uid: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val photoPath: String?,
    val roles: Set<Role>,
    val salonId: String?,
    val stylistId: String?,
    val favoriteSalonIds: Set<String>,
    val favoriteStylistKeys: Set<String>,
    val language: String,
    val notificationsEnabled: Boolean,
) {
    val isStaff: Boolean get() = Role.Owner in roles || Role.Stylist in roles
}

@Immutable
data class InboxMessage(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Instant,
    val appointmentId: String?,
    val salonId: String?,
    val dateKey: String?,
    val serviceIds: List<String>,
    val stylistId: String?,
)

@Immutable
data class WaitlistEntry(
    val id: String,
    val salonId: String,
    val salonName: String,
    val date: LocalDate,
    val serviceNames: List<String>,
    val status: String,
)

enum class TimeOffKind(val key: String) {
    Block("block"), Vacation("vacation"), Sick("sick"), Other("other");

    companion object {
        fun from(key: String?): TimeOffKind = entries.firstOrNull { it.key == key } ?: Block
    }
}

@Immutable
data class TimeOff(
    val id: String,
    val stylistId: String,
    val start: Instant,
    val end: Instant,
    val kind: TimeOffKind,
    val reason: String,
)

enum class StatsPeriod(val key: String) { Day("day"), Week("week"), Month("month") }

@Immutable
data class RankedItem(val id: String, val name: String, val count: Int)

@Immutable
data class DayPoint(val date: LocalDate, val count: Int, val revenue: Int)

@Immutable
data class SalonStats(
    val total: Int,
    val completed: Int,
    val noShow: Int,
    val canceled: Int,
    val revenueEstimate: Int,
    val noShowRate: Double,
    val topServices: List<RankedItem>,
    val topStylists: List<RankedItem>,
    val series: List<DayPoint>,
)

/** Kriterijumi pretrage i filteri. */
@Immutable
data class SearchFilters(
    val query: String = "",
    val category: ServiceCategory? = null,
    val maxPrice: Int? = null,
    val minRating: Double? = null,
    val maxDistanceKm: Double? = null,
    val availability: AvailabilityFilter = AvailabilityFilter.Any,
) {
    val isDefault: Boolean
        get() = category == null && maxPrice == null && minRating == null && maxDistanceKm == null && availability == AvailabilityFilter.Any
}

enum class AvailabilityFilter { Any, Today, Tomorrow }

/** Salon sa rastojanjem od korisnika (ako je lokacija poznata). */
@Immutable
data class SalonListItem(val salon: Salon, val distanceKm: Double?)
