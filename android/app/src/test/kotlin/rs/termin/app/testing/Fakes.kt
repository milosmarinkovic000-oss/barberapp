package rs.termin.app.testing

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentSource
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Availability
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.BookedService
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.DayPoint
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Hold
import rs.termin.app.core.domain.model.InboxMessage
import rs.termin.app.core.domain.model.Provider
import rs.termin.app.core.domain.model.RankedItem
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.model.SalonStats
import rs.termin.app.core.domain.model.SearchFilters
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.model.StatsPeriod
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.domain.repository.AppLanguage
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.BlockResult
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.DemoRole
import rs.termin.app.core.domain.repository.InboxRepository
import rs.termin.app.core.domain.repository.IntegrationRepository
import rs.termin.app.core.domain.repository.LocationProvider
import rs.termin.app.core.domain.repository.ManualBooking
import rs.termin.app.core.domain.repository.PhoneCodeEvent
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.SalonEdit
import rs.termin.app.core.domain.repository.SalonPage
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.ServiceEdit
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.repository.StylistEdit
import rs.termin.app.core.domain.repository.SyncResult
import rs.termin.app.core.domain.repository.UserRepository
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Saloni iz memorije: [salon] sa uslugama, frizerima i recenzijama iz [TestData], a [others] se
 * pojavljuju samo u listama (početna, pretraga). Test može da promeni podatke pre pokretanja ekrana.
 */
class FakeSalonRepository(
    var salon: Salon = TestData.salon(),
    var services: List<Service> = listOf(TestData.service("cut"), TestData.service("color", listOf("b"), duration = 90, price = 6000)),
    var stylists: List<Stylist> = listOf(TestData.stylist("a", "Ana"), TestData.stylist("b", "Bojan")),
) : SalonRepository {
    var others: List<Salon> = emptyList()
    var reviews: List<Review> = emptyList()
    private val all: List<Salon> get() = listOf(salon) + others

    override fun observeSalon(salonId: String): Flow<Salon?> = MutableStateFlow(all.firstOrNull { it.id == salonId })
    override fun observeServices(salonId: String, includeInactive: Boolean): Flow<List<Service>> = MutableStateFlow(services)
    override fun observeStylists(salonId: String, includeInactive: Boolean): Flow<List<Stylist>> = MutableStateFlow(stylists)
    override fun observeReviews(salonId: String, limit: Long): Flow<List<Review>> = MutableStateFlow(reviews)
    override suspend fun topSalons(limit: Long, cursor: Any?): SalonPage = SalonPage(all, null)
    override suspend fun freeToday(limit: Long): List<Salon> = all
    override suspend fun nearby(center: GeoLocation, radiusKm: Double): List<SalonListItem> =
        all.mapIndexed { i, s -> SalonListItem(s, NEAREST_KM + i * NEARBY_STEP_KM) }
    override suspend fun search(filters: SearchFilters, location: GeoLocation?): List<SalonListItem> = all.map { SalonListItem(it, null) }
    override suspend fun salonsByIds(ids: Collection<String>): List<Salon> = all.filter { it.id in ids }
    override suspend fun salonIdForSlug(slugOrId: String): String? = all.firstOrNull { slugOrId == it.id || slugOrId == it.slug }?.id

    private companion object {
        const val NEAREST_KM = 1.2
        const val NEARBY_STEP_KM = 1.5
    }
}

/**
 * Zakazivanje kao na serveru, u memoriji: slotovi 10:00–13:00 svakih 30 minuta za sledećih
 * 14 dana; zauzet slot ([takenByOthers]) daje SLOT_TAKEN sa osveženim slotovima.
 */
class FakeBookingRepository(private val clock: java.time.Clock, val salons: FakeSalonRepository) : BookingRepository {
    val takenByOthers = mutableSetOf<Instant>()
    val holds = mutableMapOf<String, Hold>()
    /** Potvrđeni termini; [FakeAppointmentRepository] ih prikazuje i kao „Moji termini“. */
    val confirmedState = MutableStateFlow<List<Appointment>>(emptyList())
    var confirmed: List<Appointment>
        get() = confirmedState.value
        set(value) {
            confirmedState.value = value
        }
    private val holdServices = mutableMapOf<String, List<Service>>()
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
        holdServices[hold.appointmentId] = services
        return hold
    }

    override suspend fun releaseHold(appointmentId: String) {
        holds.remove(appointmentId)
        released += appointmentId
    }

    override suspend fun confirmBooking(appointmentId: String, note: String, waitlistId: String?): Appointment {
        val hold = holds.remove(appointmentId) ?: throw BookingError.NotFound
        val services = holdServices.remove(appointmentId).orEmpty().map { BookedService(it.id, it.name, it.price, it.durationMin) }
        val appointment = TestData.appointment(id = appointmentId, start = hold.start, durationMin = hold.durationMin)
            .copy(
                salonId = salons.salon.id,
                salonName = salons.salon.name,
                salonPhone = salons.salon.phone,
                salonAddress = "${salons.salon.address}, ${salons.salon.city}",
                stylistId = hold.stylistId,
                stylistName = hold.stylistName,
                note = note,
                totalPrice = hold.totalPrice,
                services = services,
            )
        confirmed = confirmed + appointment
        return appointment
    }

    override suspend fun cancel(appointmentId: String, reason: String) {
        confirmed = confirmed.map { if (it.id == appointmentId) it.copy(status = AppointmentStatus.CanceledByCustomer) else it }
    }

    override suspend fun reschedule(appointmentId: String, start: Instant, stylistId: String?): Appointment {
        val current = confirmed.first { it.id == appointmentId }
        val updated = current.copy(start = start, end = start.plus(Duration.ofMinutes(current.durationMin.toLong())))
        confirmed = confirmed.map { if (it.id == appointmentId) updated else it }
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

/** Termini iz [mine] i oni potvrđeni preko [booking] (noviji podatak istog termina ima prednost). */
class FakeAppointmentRepository(private val booking: FakeBookingRepository? = null) : AppointmentRepository {
    val mine = MutableStateFlow<List<Appointment>>(emptyList())
    private val all: Flow<List<Appointment>> = combine(mine, booking?.confirmedState ?: flowOf(emptyList())) { own, confirmed ->
        own.filter { o -> confirmed.none { it.id == o.id } } + confirmed
    }
    override fun observeMine(uid: String): Flow<List<Appointment>> = all
    override fun observeAppointment(appointmentId: String): Flow<Appointment?> =
        all.map { list -> list.firstOrNull { it.id == appointmentId } }
    override fun observeSalonAppointments(salonId: String, from: Instant, to: Instant, stylistId: String?): Flow<List<Appointment>> =
        all.map { list -> list.filter { !it.start.isBefore(from) && it.start.isBefore(to) && (stylistId == null || it.stylistId == stylistId) } }
    override suspend fun customerHistory(salonId: String, customerUid: String?, customerPhone: String?): List<Appointment> = all.first()
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

class FakeConnectivity(initiallyOnline: Boolean = true) : ConnectivityMonitor {
    val online = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = online
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
    override suspend fun demoSignIn(role: DemoRole, activity: Activity) {
        state.value = when (role) {
            DemoRole.Customer -> AuthState.SignedIn("u1", setOf(Role.Customer), null, null)
            DemoRole.Owner -> AuthState.SignedIn("owner", setOf(Role.Customer, Role.Owner), "s1", null)
            DemoRole.Stylist -> AuthState.SignedIn("stylist-a", setOf(Role.Customer, Role.Stylist), "s1", "a")
        }
    }
    override suspend fun refreshClaims() = Unit
    override suspend fun signOut() {
        state.value = AuthState.SignedOut
    }
}

class FakeInboxRepository : InboxRepository {
    val messages = MutableStateFlow<List<InboxMessage>>(emptyList())
    override fun observeInbox(uid: String): Flow<List<InboxMessage>> = messages
    override suspend fun markRead(uid: String, messageId: String) = markAllRead(uid, listOf(messageId))
    override suspend fun markAllRead(uid: String, ids: List<String>) = messages.update { list ->
        list.map { if (it.id in ids) it.copy(read = true) else it }
    }
    override suspend fun delete(uid: String, messageId: String) = messages.update { list -> list.filterNot { it.id == messageId } }
}

/** Kao u demo režimu: nijedna integracija nije podešena na serveru, pa se povezivanje ne nudi. */
class FakeIntegrationRepository : IntegrationRepository {
    override suspend fun serverStatus(): Map<Provider, Boolean> = Provider.entries.associateWith { false }
    override suspend fun startOAuth(provider: Provider, salonId: String, stylistId: String): String = notConfigured()
    override suspend fun setEnabled(provider: Provider, salonId: String, stylistId: String, enabled: Boolean) = notConfigured()
    override suspend fun disconnect(provider: Provider, salonId: String, stylistId: String) = notConfigured()

    private fun notConfigured(): Nothing = throw IllegalStateException("Integracije nisu podešene")
}

/** Lokacija je dostupna samo kad je test postavi. */
class FakeLocationProvider(var location: GeoLocation? = null) : LocationProvider {
    override fun hasPermission(): Boolean = location != null
    override suspend fun currentLocation(): GeoLocation? = location
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

/**
 * Osoblje: odsustva, promene statusa i ručni termini iz memorije. Ručni termin ulazi u [bookings],
 * pa ga vide i kalendar i lista termina; blokada vremena prijavljuje termine sa kojima se preklapa.
 */
class FakeStaffRepository(private val bookings: FakeBookingRepository? = null) : StaffRepository {
    val timeOff = MutableStateFlow<List<TimeOff>>(emptyList())
    val statusChanges = mutableListOf<Pair<String, AppointmentStatus>>()

    override fun observeTimeOff(salonId: String, stylistId: String): Flow<List<TimeOff>> =
        timeOff.map { list -> list.filter { it.stylistId == stylistId } }
    override suspend fun updateStatus(appointmentId: String, status: AppointmentStatus, reason: String) {
        statusChanges += appointmentId to status
    }
    override suspend fun updateSalon(salonId: String, edit: SalonEdit) = unused()
    override suspend fun uploadSalonPhoto(salonId: String, uri: Uri): String = unused()
    override suspend fun setSalonPhotos(salonId: String, photos: List<String>) = unused()
    override suspend fun setSalonBranding(salonId: String, coverPhoto: String?, accentColor: Long) = unused()
    override suspend fun upsertService(salonId: String, serviceId: String?, edit: ServiceEdit) = unused()
    override suspend fun deleteService(salonId: String, serviceId: String) = unused()
    override suspend fun inviteStylist(salonId: String, phoneE164: String, name: String): Boolean = unused()
    override suspend fun updateStylist(salonId: String, stylistId: String, edit: StylistEdit) = unused()
    override suspend fun updateStylistProfile(salonId: String, stylistId: String, bio: String, specialties: List<String>) = unused()
    override suspend fun uploadStylistPhoto(salonId: String, stylistId: String, uri: Uri, portfolio: Boolean): String = unused()
    override suspend fun setStylistPortfolio(salonId: String, stylistId: String, portfolio: List<String>) = unused()
    override suspend fun setStylistAccess(salonId: String, stylistId: String, enabled: Boolean) = unused()
    override suspend fun blockTime(
        salonId: String,
        stylistId: String,
        start: Instant,
        end: Instant,
        kind: TimeOffKind,
        reason: String,
    ): BlockResult {
        val off = TimeOff("off-${timeOff.value.size + 1}", stylistId, start, end, kind, reason)
        timeOff.update { it + off }
        val conflicts = bookings?.confirmed.orEmpty().count { it.stylistId == stylistId && it.status.isActive && it.start < end && start < it.end }
        return BlockResult(off.id, conflicts)
    }
    override suspend fun removeTimeOff(salonId: String, stylistId: String, timeOffId: String) = timeOff.update { list ->
        list.filterNot { it.id == timeOffId }
    }
    override suspend fun createManualBooking(booking: ManualBooking): Appointment {
        val target = bookings ?: unused()
        val services = target.salons.services.filter { it.id in booking.serviceIds }
        val stylist = target.salons.stylists.first { it.id == booking.stylistId }
        val appointment = TestData.appointment(
            id = "manual-${target.confirmed.size + 1}",
            start = booking.start,
            durationMin = services.sumOf { it.durationMin },
            customerUid = null,
        ).copy(
            stylistId = stylist.id,
            stylistName = stylist.name,
            customerName = booking.customerName,
            customerPhone = booking.customerPhone,
            services = services.map { BookedService(it.id, it.name, it.price, it.durationMin) },
            totalPrice = services.sumOf { it.price },
            note = booking.note,
            source = AppointmentSource.Manual,
        )
        target.confirmed = target.confirmed + appointment
        return appointment
    }
    override suspend fun replyToReview(salonId: String, reviewId: String, text: String) = unused()
    /** Statistika nad terminima iz [bookings] u danu, nedelji (od ponedeljka) ili mesecu kojem pripada [date]. */
    override suspend fun stats(salonId: String, period: StatsPeriod, date: LocalDate): SalonStats {
        val range = when (period) {
            StatsPeriod.Day -> date..date
            StatsPeriod.Week -> date.with(DayOfWeek.MONDAY).let { it..it.plusDays(6) }
            StatsPeriod.Month -> date.withDayOfMonth(1).let { it..it.plusMonths(1).minusDays(1) }
        }
        val list = bookings?.confirmed.orEmpty().filter { it.start.atZone(BelgradeZone).toLocalDate() in range }
        val done = list.filter { it.status == AppointmentStatus.Completed || it.status == AppointmentStatus.Confirmed }
        val noShow = list.count { it.status == AppointmentStatus.NoShow }
        fun ranked(key: (Appointment) -> List<Pair<String, String>>) = done.flatMap(key)
            .groupingBy { it }.eachCount()
            .map { (item, count) -> RankedItem(item.first, item.second, count) }
            .sortedByDescending { it.count }
        return SalonStats(
            total = list.size,
            completed = list.count { it.status == AppointmentStatus.Completed },
            noShow = noShow,
            canceled = list.count { it.status.isCanceled },
            revenueEstimate = done.sumOf { it.totalPrice },
            noShowRate = if (list.isEmpty()) 0.0 else noShow.toDouble() / list.size,
            topServices = ranked { a -> a.services.map { it.id to it.name } },
            topStylists = ranked { a -> listOf(a.stylistId to a.stylistName) },
            series = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.map { day ->
                val items = done.filter { it.start.atZone(BelgradeZone).toLocalDate() == day }
                DayPoint(day, items.size, items.sumOf { it.totalPrice })
            }.toList(),
        )
    }

    private fun unused(): Nothing = throw UnsupportedOperationException("Ovaj test ne koristi tu radnju")
}
