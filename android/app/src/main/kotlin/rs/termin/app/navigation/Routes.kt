package rs.termin.app.navigation

import kotlinx.serialization.Serializable

// Tipizovane rute (Navigation Compose + kotlinx.serialization).

// ---- Klijent: glavni tabovi ----
@Serializable data object HomeRoute

@Serializable data class SearchRoute(val category: String? = null, val freeToday: Boolean = false, val focus: Boolean = false)

@Serializable data object AppointmentsRoute

@Serializable data object ProfileRoute

// ---- Osoblje: glavni tabovi ----
@Serializable data object CalendarRoute

@Serializable data object StaffAppointmentsRoute

@Serializable data object SalonManageRoute

// ---- Detalji ----
@Serializable data class SalonRoute(val salonId: String)

/**
 * Tok zakazivanja. [services] je lista id-jeva usluga odvojenih zarezom; [stylist] je izabrani
 * frizer (prazno = bilo koji); [rescheduleId] menja postojeći termin umesto novog.
 */
@Serializable
data class BookingRoute(
    val salonId: String,
    val services: String? = null,
    val stylist: String? = null,
    val date: String? = null,
    val rescheduleId: String? = null,
)

@Serializable data class BookingSuccessRoute(val appointmentId: String)

@Serializable data class AppointmentRoute(val appointmentId: String)

@Serializable data object InboxRoute

@Serializable data object FavoritesRoute

@Serializable data object SettingsRoute

@Serializable data object AppearanceRoute

@Serializable data object PrivacyRoute

@Serializable data object LicensesRoute

@Serializable data object DeleteAccountRoute

@Serializable data object CreateSalonRoute

@Serializable data object EditProfileRoute

@Serializable data object StylePreviewRoute

// ---- Vlasnik / frizer ----
@Serializable data class StaffAppointmentRoute(val appointmentId: String)

@Serializable data object SalonInfoRoute

@Serializable data object ServicesRoute

@Serializable data class ServiceEditRoute(val serviceId: String? = null)

@Serializable data object StylistsRoute

@Serializable data class StylistEditRoute(val stylistId: String)

@Serializable data class TimeOffRoute(val stylistId: String)

@Serializable data class IntegrationsRoute(val provider: String? = null, val status: String? = null, val reason: String? = null)

@Serializable data object StatsRoute

@Serializable data object ReviewsManageRoute

@Serializable data object SalonAppearanceRoute
