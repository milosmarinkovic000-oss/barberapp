package rs.termin.app.core.domain.repository

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Availability
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Hold
import rs.termin.app.core.domain.model.InboxMessage
import rs.termin.app.core.domain.model.Provider
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.SalonStats
import rs.termin.app.core.domain.model.SearchFilters
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.domain.model.StatsPeriod
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.domain.model.WeeklyHours
import java.time.Instant
import java.time.LocalDate

// ---------------- Prijava ----------------

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val roles: Set<Role>, val salonId: String?, val stylistId: String?) : AuthState
}

sealed interface PhoneCodeEvent {
    data class CodeSent(val verificationId: String) : PhoneCodeEvent
    data object AutoVerified : PhoneCodeEvent
}

enum class DemoRole { Customer, Owner, Stylist }

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val isGoogleSignInAvailable: Boolean

    /** Šalje SMS kod; u emulatoru se kod vidi u Emulator UI. */
    suspend fun sendCode(phoneE164: String, activity: Activity): PhoneCodeEvent

    suspend fun verifyCode(verificationId: String, code: String)

    /** Google nalog preko Credential Manager-a. */
    suspend fun signInWithGoogle(activity: Activity, emulatorEmail: String? = null)

    /** Samo debug + emulator: prijava demo nalogom jednim dodirom (kod se čita iz emulatora). */
    suspend fun demoSignIn(role: DemoRole, activity: Activity)

    /** Ponovo čita uloge (custom claims) posle promene na serveru. */
    suspend fun refreshClaims()

    suspend fun signOut()
}

// ---------------- Korisnik ----------------

data class SyncResult(val roles: Set<Role>, val salonId: String?, val stylistId: String?, val isNew: Boolean)

interface UserRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun syncAccount(language: String, name: String? = null): SyncResult
    suspend fun updateName(name: String)
    suspend fun updatePhoto(uri: Uri)
    suspend fun setLanguage(language: String)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun toggleFavoriteSalon(salonId: String, favorite: Boolean)
    suspend fun toggleFavoriteStylist(key: String, favorite: Boolean)
    suspend fun registerPushToken()
    suspend fun savePushId(installationId: String)
    suspend fun createSalon(name: String, city: String, address: String, phone: String, location: GeoLocation): String
    suspend fun deleteAccount()
}

// ---------------- Saloni ----------------

data class SalonPage(val items: List<Salon>, val nextCursor: Any?)

interface SalonRepository {
    fun observeSalon(salonId: String): Flow<Salon?>
    fun observeServices(salonId: String, includeInactive: Boolean = false): Flow<List<Service>>
    fun observeStylists(salonId: String, includeInactive: Boolean = false): Flow<List<Stylist>>
    fun observeReviews(salonId: String, limit: Long = 20): Flow<List<Review>>
    suspend fun topSalons(limit: Long = 20, cursor: Any? = null): SalonPage
    suspend fun freeToday(limit: Long = 10): List<Salon>
    suspend fun nearby(center: GeoLocation, radiusKm: Double): List<SalonListItem>
    suspend fun search(filters: SearchFilters, location: GeoLocation?): List<SalonListItem>
    suspend fun salonsByIds(ids: Collection<String>): List<Salon>
    suspend fun salonIdForSlug(slugOrId: String): String?
}

// ---------------- Zakazivanje ----------------

interface BookingRepository {
    suspend fun availability(
        salonId: String,
        serviceIds: List<String>,
        stylistId: String?,
        fromDate: LocalDate,
        days: Int,
        excludeAppointmentId: String? = null,
    ): Availability

    suspend fun holdSlot(salonId: String, serviceIds: List<String>, stylistId: String?, start: Instant, replaceHoldId: String?): Hold
    suspend fun releaseHold(appointmentId: String)
    suspend fun confirmBooking(appointmentId: String, note: String, waitlistId: String? = null): Appointment
    suspend fun cancel(appointmentId: String, reason: String = "")
    suspend fun reschedule(appointmentId: String, start: Instant, stylistId: String?): Appointment
    suspend fun joinWaitlist(salonId: String, date: LocalDate, serviceIds: List<String>, stylistId: String?): String
    suspend fun leaveWaitlist(waitlistId: String)
    suspend fun submitReview(appointmentId: String, rating: Int, text: String)
}

interface AppointmentRepository {
    fun observeMine(uid: String): Flow<List<Appointment>>
    fun observeAppointment(appointmentId: String): Flow<Appointment?>
    fun observeSalonAppointments(salonId: String, from: Instant, to: Instant, stylistId: String? = null): Flow<List<Appointment>>
    suspend fun customerHistory(salonId: String, customerUid: String?, customerPhone: String?): List<Appointment>
    fun observeMyWaitlist(uid: String): Flow<List<WaitlistEntry>>
}

interface InboxRepository {
    fun observeInbox(uid: String): Flow<List<InboxMessage>>
    suspend fun markRead(uid: String, messageId: String)
    suspend fun markAllRead(uid: String, ids: List<String>)
    suspend fun delete(uid: String, messageId: String)
}

// ---------------- Osoblje salona ----------------

data class SalonEdit(
    val name: String,
    val description: String,
    val address: String,
    val city: String,
    val location: GeoLocation?,
    val phone: String,
    val workingHours: WeeklyHours,
    val rules: SalonRules,
    val published: Boolean,
    val priceLevel: Int,
)

data class ServiceEdit(
    val category: ServiceCategory,
    val name: String,
    val description: String,
    val durationMin: Int,
    val price: Int,
    val stylistIds: List<String>,
    val active: Boolean,
    val order: Int,
)

data class StylistEdit(
    val name: String,
    val bio: String,
    val specialties: List<String>,
    val workingHours: WeeklyHours?,
    val breaks: WeeklyHours,
    val active: Boolean,
)

data class ManualBooking(
    val salonId: String,
    val stylistId: String,
    val serviceIds: List<String>,
    val start: Instant,
    val customerName: String,
    val customerPhone: String?,
    val note: String,
    val walkIn: Boolean,
)

data class BlockResult(val timeOffId: String, val conflicts: Int)

interface StaffRepository {
    suspend fun updateSalon(salonId: String, edit: SalonEdit)
    suspend fun uploadSalonPhoto(salonId: String, uri: Uri): String
    suspend fun setSalonPhotos(salonId: String, photos: List<String>)
    suspend fun setSalonBranding(salonId: String, coverPhoto: String?, accentColor: Long)
    suspend fun upsertService(salonId: String, serviceId: String?, edit: ServiceEdit)
    suspend fun deleteService(salonId: String, serviceId: String)
    suspend fun inviteStylist(salonId: String, phoneE164: String, name: String): Boolean
    suspend fun updateStylist(salonId: String, stylistId: String, edit: StylistEdit)
    suspend fun updateStylistProfile(salonId: String, stylistId: String, bio: String, specialties: List<String>)
    suspend fun uploadStylistPhoto(salonId: String, stylistId: String, uri: Uri, portfolio: Boolean): String
    suspend fun setStylistPortfolio(salonId: String, stylistId: String, portfolio: List<String>)
    suspend fun setStylistAccess(salonId: String, stylistId: String, enabled: Boolean)
    fun observeTimeOff(salonId: String, stylistId: String): Flow<List<TimeOff>>
    suspend fun blockTime(salonId: String, stylistId: String, start: Instant, end: Instant, kind: TimeOffKind, reason: String): BlockResult
    suspend fun removeTimeOff(salonId: String, stylistId: String, timeOffId: String)
    suspend fun createManualBooking(booking: ManualBooking): Appointment
    suspend fun updateStatus(appointmentId: String, status: AppointmentStatus, reason: String = "")
    suspend fun replyToReview(salonId: String, reviewId: String, text: String)
    suspend fun stats(salonId: String, period: StatsPeriod, date: LocalDate): SalonStats
}

interface IntegrationRepository {
    /** Koji provajderi su podešeni na serveru (u demo režimu nijedan). */
    suspend fun serverStatus(): Map<Provider, Boolean>
    suspend fun startOAuth(provider: Provider, salonId: String, stylistId: String): String
    suspend fun setEnabled(provider: Provider, salonId: String, stylistId: String, enabled: Boolean)
    suspend fun disconnect(provider: Provider, salonId: String, stylistId: String)
}

// ---------------- Uređaj ----------------

enum class AppLanguage(val tag: String?) { System(null), Serbian("sr-Latn"), English("en") }

data class AppPreferences(
    val onboardingDone: Boolean = false,
    val appearance: Appearance = Appearance(),
    val language: AppLanguage = AppLanguage.System,
    val notificationPermissionAsked: Boolean = false,
    val firstBookingDone: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    /** Samo debug: izbor sa ekrana „Pregled stila“. */
    val debugThemeConfig: ThemeConfig? = null,
)

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun setOnboardingDone()
    suspend fun setAppearance(appearance: Appearance)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setNotificationPermissionAsked()
    suspend fun setFirstBookingDone()
    suspend fun addRecentSearch(query: String)
    suspend fun setDebugThemeConfig(config: ThemeConfig?)
}

interface LocationProvider {
    fun hasPermission(): Boolean
    suspend fun currentLocation(): GeoLocation?
}

interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}

/** Lokalni podsetnici (WorkManager): rade i bez interneta i u demo režimu. */
interface ReminderScheduler {
    fun schedule(appointment: Appointment)
    fun cancel(appointmentId: String)
    fun cancelAll()
}
