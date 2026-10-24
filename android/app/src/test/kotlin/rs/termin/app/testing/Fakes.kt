package rs.termin.app.testing

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Availability
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.BookedService
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Hold
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.model.SearchFilters
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.domain.repository.AppLanguage
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.DemoRole
import rs.termin.app.core.domain.repository.PhoneCodeEvent
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.SalonPage
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.SyncResult
import rs.termin.app.core.domain.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Salon iz memorije: jedan salon, usluge i frizeri iz [TestData]. */
class FakeSalonRepository(
    val salon: Salon = TestData.salon(),
    val services: List<Service> = listOf(TestData.service("cut"), TestData.service("color", listOf("b"), duration = 90, price = 6000)),
    val stylists: List<Stylist> = listOf(TestData.stylist("a", "Ana"), TestData.stylist("b", "Bojan")),
) : SalonRepository {
    override fun observeSalon(salonId: String): Flow<Salon?> = MutableStateFlow(salon.takeIf { it.id == salonId })
    override fun observeServices(salonId: String, includeInactive: Boolean): Flow<List<Service>> = MutableStateFlow(services)
    override fun observeStylists(salonId: String, includeInactive: Boolean): Flow<List<Stylist>> = MutableStateFlow(stylists)
    override fun observeReviews(salonId: String, limit: Long): Flow<List<Review>> = MutableStateFlow(emptyList())
    override suspend fun topSalons(limit: Long, cursor: Any?): SalonPage = SalonPage(listOf(salon), null)
    override suspend fun freeToday(limit: Long): List<Salon> = listOf(salon)
    override suspend fun nearby(center: GeoLocation, radiusKm: Double): List<SalonListItem> = listOf(SalonListItem(salon, 1.2))
    override suspend fun search(filters: SearchFilters, location: GeoLocation?): List<SalonListItem> = listOf(SalonListItem(salon, null))
    override suspend fun salonsByIds(ids: Collection<String>): List<Salon> = listOf(salon).filter { it.id in ids }
    override suspend fun salonIdForSlug(slugOrId: String): String? = salon.id.takeIf { slugOrId == salon.id || slugOrId == salon.slug }
}

/**
 * Zakazivanje kao na serveru, u memoriji: slotovi 10:00–13:00 svakih 30 minuta za sledećih
 * 14 dana; zauzet slot ([takenByOthers]) daje SLOT_TAKEN sa osveženim slotovima.
 */
class FakeBookingRepository(private val clock: java.time.Clock, private val salons: FakeSalonRepository) : BookingRepository {
    val takenByOthers = mutableSetOf<Instant>()
    val holds = mutableMapOf<String, Hold>()
    val confirmed = mutableListOf<Appointment>()
    val released = mutableListOf<String>()
    val waitlist = mutableListOf<Pair<LocalDate, List<String>>>()
    private var counter = 0

    private fun slotsFor(date: LocalDate): List<Slot> =
        (0 until SLOTS).map { date.atTime(LocalTime.of(10, 0).plusMinutes(it * 30L)).atZone(BelgradeZone).toInstant() }
            .filter { it.isAfter(clock.instant()) && it !in takenByOthers && holds.values.none { h -> h.start == it } }
            .map { Slot(it, salons.stylists.map { s -> s.id }) }

    override suspend fun availability(
        salonId: String,
        serviceIds: List<String>,
        stylistId: String?,
        fromDate: LocalDate,
        days: Int,
        excludeAppointmentId: String?,
    ): Availability {
        val services = salons.services.filter { it.id in serviceIds }
        return Availability(
            salonId = salonId,
            durationMin = services.sumOf { it.durationMin },
            totalPrice = services.sumOf { it.price },
            stylistIds = salons.stylists.map { it.id },
            days = (0 until days).map { fromDate.plusDays(it.toLong()) }.map { DaySlots(it, slotsFor(it)) },
        )
    }

    override suspend fun holdSlot(salonId: String, serviceIds: List<String>, stylistId: String?, start: Instant, replaceHoldId: String?): Hold {
        if (start in takenByOthers) {
            throw BookingError.SlotTaken(slotsFor(start.atZone(BelgradeZone).toLocalDate()))
        }
        replaceHoldId?.let { holds.remove(it) }
        val services = salons.services.filter { it.id in serviceIds }
        val duration = services.sumOf { it.durationMin }
        val stylist = salons.stylists.first { stylistId == null || it.id == stylistId }
        val hold = Hold(
            appointmentId = "appt-${++counter}",
            stylistId = stylist.id,
            stylistName = stylist.name,
            start = start,
            end = start.plus(Duration.ofMinutes(duration.toLong())),
            expiresAt = clock.instant().plus(Duration.ofMinutes(5)),
            durationMin = duration,
            totalPrice = services.sumOf { it.price },
        )
        holds[hold.appointmentId] = hold
        return hold
    }

    override suspend fun releaseHold(appointmentId: String) {
        holds.remove(appointmentId)
        released += appointmentId
    }

    override suspend fun confirmBooking(appointmentId: String, note: String, waitlistId: String?): Appointment {
        val hold = holds.remove(appointmentId) ?: throw BookingError.NotFound
        val appointment = TestData.appointment(id = appointmentId, start = hold.start, durationMin = hold.durationMin)
            .copy(stylistId = hold.stylistId, stylistName = hold.stylistName, note = note, totalPrice = hold.totalPrice, services = listOf(BookedService("cut", "Šišanje", hold.totalPrice, hold.durationMin)))
        confirmed += appointment
        return appointment
    }

    override suspend fun cancel(appointmentId: String, reason: String) {
        confirmed.replaceAll { if (it.id == appointmentId) it.copy(status = AppointmentStatus.CanceledByCustomer) else it }
    }

    override suspend fun reschedule(appointmentId: String, start: Instant, stylistId: String?): Appointment {
        val index = confirmed.indexOfFirst { it.id == appointmentId }
        val updated = confirmed[index].copy(start = start, end = start.plus(Duration.ofMinutes(confirmed[index].durationMin.toLong())))
        confirmed[index] = updated
        return updated
    }

    override suspend fun joinWaitlist(salonId: String, date: LocalDate, serviceIds: List<String>, stylistId: String?): String {
        waitlist += date to serviceIds
        return "w-${waitlist.size}"
    }

    override suspend fun leaveWaitlist(waitlistId: String) = Unit

    override suspend fun submitReview(appointmentId: String, rating: Int, text: String) = Unit

    private companion object {
        const val SLOTS = 6
    }
}

class FakeAppointmentRepository(private val booking: FakeBookingRepository? = null) : AppointmentRepository {
    val mine = MutableStateFlow<List<Appointment>>(emptyList())
    override fun observeMine(uid: String): Flow<List<Appointment>> = mine
    override fun observeAppointment(appointmentId: String): Flow<Appointment?> =
        mine.map { list -> list.firstOrNull { it.id == appointmentId } ?: booking?.confirmed?.firstOrNull { it.id == appointmentId } }
    override fun observeSalonAppointments(salonId: String, from: Instant, to: Instant, stylistId: String?): Flow<List<Appointment>> =
        mine.map { list -> list.filter { !it.start.isBefore(from) && it.start.isBefore(to) && (stylistId == null || it.stylistId == stylistId) } }
    override suspend fun customerHistory(salonId: String, customerUid: String?, customerPhone: String?): List<Appointment> = mine.value
    override fun observeMyWaitlist(uid: String): Flow<List<WaitlistEntry>> = MutableStateFlow(emptyList())
}

class FakePreferences(initial: AppPreferences = AppPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<AppPreferences> = state
    override suspend fun setOnboardingDone() = state.update { it.copy(onboardingDone = true) }
    override suspend fun setAppearance(appearance: Appearance) = state.update { it.copy(appearance = appearance) }
    override suspend fun setLanguage(language: AppLanguage) = state.update { it.copy(language = language) }
    override suspend fun setNotificationPermissionAsked() = state.update { it.copy(notificationPermissionAsked = true) }
    override suspend fun setFirstBookingDone() = state.update { it.copy(firstBookingDone = true) }
    override suspend fun addRecentSearch(query: String) = state.update { it.copy(recentSearches = listOf(query) + it.recentSearches) }
    override suspend fun setClientView(clientView: Boolean) = state.update { it.copy(clientView = clientView) }
    override suspend fun setDebugThemeConfig(config: ThemeConfig?) = state.update { it.copy(debugThemeConfig = config) }

    private fun MutableStateFlow<AppPreferences>.update(change: (AppPreferences) -> AppPreferences) {
        value = change(value)
    }
}

class FakeReminders : ReminderScheduler {
    val scheduled = mutableListOf<String>()
    val canceled = mutableListOf<String>()
    override fun schedule(appointment: Appointment) {
        scheduled += appointment.id
    }
    override fun cancel(appointmentId: String) {
        canceled += appointmentId
    }
    override fun cancelAll() {
        canceled += scheduled
        scheduled.clear()
    }
}

class FakeConnectivity(online: Boolean = true) : ConnectivityMonitor {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(online)
}

class FakeAuthRepository(initial: AuthState = AuthState.SignedOut) : AuthRepository {
    val state = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = state
    override val isGoogleSignInAvailable: Boolean = true
    override suspend fun sendCode(phoneE164: String, activity: Activity): PhoneCodeEvent = PhoneCodeEvent.CodeSent("verification")
    override suspend fun verifyCode(verificationId: String, code: String) {
        state.value = AuthState.SignedIn("u1", setOf(Role.Customer), null, null)
    }
    override suspend fun signInWithGoogle(activity: Activity, emulatorEmail: String?) = verifyCode("google", "")
    override suspend fun demoSignIn(role: DemoRole, activity: Activity) = verifyCode("demo", "")
    override suspend fun refreshClaims() = Unit
    override suspend fun signOut() {
        state.value = AuthState.SignedOut
    }
}

/** Korisnik iz memorije; [syncFailure] simulira prvu prijavu bez interneta. */
class FakeUserRepository(var profile: UserProfile? = null) : UserRepository {
    val flow = MutableStateFlow(profile)
    var syncFailure: Exception? = null
    var syncCalls = 0
    var pushRegistrations = 0

    override fun observeProfile(): Flow<UserProfile?> = flow
    override suspend fun syncAccount(language: String, name: String?): SyncResult {
        syncCalls++
        syncFailure?.let { throw it }
        if (flow.value == null) flow.value = TestProfiles.customer(name = "")
        return SyncResult(flow.value?.roles.orEmpty(), flow.value?.salonId, flow.value?.stylistId, isNew = true)
    }
    override suspend fun updateName(name: String) {
        flow.value = flow.value?.copy(name = name)
    }
    override suspend fun updatePhoto(uri: Uri) = Unit
    override suspend fun setLanguage(language: String) = Unit
    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        flow.value = flow.value?.copy(notificationsEnabled = enabled)
    }
    override suspend fun toggleFavoriteSalon(salonId: String, favorite: Boolean) {
        flow.value = flow.value?.let { p -> p.copy(favoriteSalonIds = if (favorite) p.favoriteSalonIds + salonId else p.favoriteSalonIds - salonId) }
    }
    override suspend fun toggleFavoriteStylist(key: String, favorite: Boolean) {
        flow.value = flow.value?.let { p -> p.copy(favoriteStylistKeys = if (favorite) p.favoriteStylistKeys + key else p.favoriteStylistKeys - key) }
    }
    override suspend fun registerPushToken() {
        pushRegistrations++
    }
    override suspend fun savePushId(installationId: String) = Unit
    override suspend fun createSalon(name: String, city: String, address: String, phone: String, location: GeoLocation): String = "new-salon"
    override suspend fun deleteAccount() {
        flow.value = null
    }
}

object TestProfiles {
    fun customer(name: String = "Marko Marković") = UserProfile(
        uid = "u1",
        name = name,
        phone = "+381600000001",
        email = null,
        photoPath = null,
        roles = setOf(Role.Customer),
        salonId = null,
        stylistId = null,
        favoriteSalonIds = emptySet(),
        favoriteStylistKeys = emptySet(),
        language = "sr",
        notificationsEnabled = true,
    )
}
