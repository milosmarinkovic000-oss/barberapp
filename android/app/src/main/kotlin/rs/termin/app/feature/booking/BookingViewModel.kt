package rs.termin.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.termin.app.core.common.ApplicationScope
import rs.termin.app.core.domain.belgradeDate
import rs.termin.app.core.domain.firstFreeSlot
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.Availability
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.Hold
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.totalsOf
import rs.termin.app.core.domain.usecase.ConfirmBookingUseCase
import rs.termin.app.core.domain.usecase.GetAvailabilityUseCase
import rs.termin.app.core.domain.usecase.HoldSlotUseCase
import rs.termin.app.core.domain.usecase.RescheduleUseCase
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.loadable
import rs.termin.app.navigation.BookingRoute
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class BookingStep { Services, Stylist, Time, Review }

data class BookingData(
    val salon: Salon,
    val services: List<Service>,
    val stylists: List<Stylist>,
    /** Termin koji se menja (režim promene termina). */
    val rescheduling: Appointment? = null,
)

data class BookingUiState(
    val data: Loadable<BookingData> = Loadable.Loading,
    val step: BookingStep = BookingStep.Services,
    val steps: List<BookingStep> = BookingStep.entries,
    val selectedServiceIds: List<String> = emptyList(),
    /** null = „Bilo koji slobodan“. */
    val stylistId: String? = null,
    val availability: Loadable<Availability>? = null,
    val selectedDate: LocalDate? = null,
    val selectedSlot: Slot? = null,
    val hold: Hold? = null,
    val holding: Boolean = false,
    val holdSecondsLeft: Long = 0,
    val holdExpired: Boolean = false,
    val note: String = "",
    val confirming: Boolean = false,
    val error: Throwable? = null,
    val waitlistJoined: Boolean = false,
    val joiningWaitlist: Boolean = false,
    val online: Boolean = true,
    val completedAppointmentId: String? = null,
    val today: LocalDate = LocalDate.now(BelgradeZone),
) {
    val services: List<Service>
        get() = data.valueOrNull()?.services?.filter { it.id in selectedServiceIds }.orEmpty()
    val totalPrice: Int get() = data.valueOrNull()?.rescheduling?.totalPrice ?: totalsOf(services).price
    val totalDuration: Int get() = data.valueOrNull()?.rescheduling?.durationMin ?: totalsOf(services).durationMin
    val isReschedule: Boolean get() = data.valueOrNull()?.rescheduling != null
    val stepIndex: Int get() = steps.indexOf(step).coerceAtLeast(0)

    /** Frizeri koji rade sve izabrane usluge. */
    val capableStylists: List<Stylist>
        get() {
            val d = data.valueOrNull() ?: return emptyList()
            return d.stylists.filter { st -> services.all { st.id in it.stylistIds } }
        }

    val selectedDay: DaySlots?
        get() = availability?.valueOrNull()?.days?.firstOrNull { it.date == selectedDate }

    val canContinue: Boolean
        get() = when (step) {
            BookingStep.Services -> selectedServiceIds.isNotEmpty()
            BookingStep.Stylist -> true
            BookingStep.Time -> (if (isReschedule) selectedSlot != null else hold != null && !holdExpired)
            BookingStep.Review -> true
        }
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val salons: SalonRepository,
    private val appointments: AppointmentRepository,
    private val booking: BookingRepository,
    private val getAvailability: GetAvailabilityUseCase,
    private val holdSlot: HoldSlotUseCase,
    private val confirmBooking: ConfirmBookingUseCase,
    private val reschedule: RescheduleUseCase,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BookingRoute>()
    private val state = MutableStateFlow(BookingUiState(today = LocalDate.now(clock.withZone(BelgradeZone))))
    private var countdown: Job? = null
    private var availabilityJob: Job? = null

    val uiState: StateFlow<BookingUiState> = combine(state, connectivity.isOnline) { s, online -> s.copy(online = online) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), state.value)

    init {
        load()
    }

    fun load() {
        state.update { it.copy(data = Loadable.Loading, error = null) }
        viewModelScope.launch {
            val result = loadable {
                val salon = salons.observeSalon(route.salonId).first() ?: throw BookingError.NotFound
                val services = salons.observeServices(route.salonId).first()
                val stylists = salons.observeStylists(route.salonId).first()
                val rescheduling = route.rescheduleId?.let { appointments.observeAppointment(it).first() ?: throw BookingError.NotFound }
                BookingData(salon, services, stylists, rescheduling)
            }
            state.update { s ->
                val data = result.valueOrNull()
                if (data == null) return@update s.copy(data = result)
                val rescheduling = data.rescheduling
                if (rescheduling != null) {
                    s.copy(
                        data = result,
                        steps = listOf(BookingStep.Time, BookingStep.Review),
                        step = BookingStep.Time,
                        selectedServiceIds = rescheduling.services.map { it.id },
                        stylistId = if (rescheduling.anyStylist) null else rescheduling.stylistId,
                    )
                } else {
                    val preselected = route.services?.split(',')?.filter { id -> data.services.any { it.id == id } }.orEmpty()
                    val stylist = route.stylist?.takeIf { id -> data.stylists.any { it.id == id } }
                    s.copy(
                        data = result,
                        selectedServiceIds = preselected,
                        stylistId = stylist,
                        selectedDate = route.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        step = when {
                            preselected.isEmpty() -> BookingStep.Services
                            stylist != null -> BookingStep.Time
                            else -> BookingStep.Stylist
                        },
                    )
                }
            }
            if (state.value.step == BookingStep.Time) loadAvailability()
        }
    }

    fun toggleService(id: String) {
        state.update {
            val ids = if (id in it.selectedServiceIds) it.selectedServiceIds - id else it.selectedServiceIds + id
            // Promena usluga poništava izbor frizera ako on više ne radi sve usluge.
            val stylistStillCapable = it.stylistId == null ||
                it.data.valueOrNull()?.services?.filter { s -> s.id in ids }?.all { s -> it.stylistId in s.stylistIds } == true
            it.copy(selectedServiceIds = ids, stylistId = if (stylistStillCapable) it.stylistId else null, availability = null)
        }
        releaseCurrentHold()
    }

    fun selectStylist(id: String?) {
        state.update { it.copy(stylistId = id, availability = null) }
        releaseCurrentHold()
    }

    fun next() {
        val s = state.value
        if (!s.canContinue) return
        val index = s.steps.indexOf(s.step)
        if (index >= s.steps.lastIndex) return
        val next = s.steps[index + 1]
        state.update { it.copy(step = next, error = null) }
        if (next == BookingStep.Time && state.value.availability == null) loadAvailability()
    }

    /** Vraća false kad je tok na prvom koraku (ekran tada treba da se zatvori). */
    fun back(): Boolean {
        val s = state.value
        val index = s.steps.indexOf(s.step)
        if (index <= 0) return false
        state.update { it.copy(step = s.steps[index - 1], error = null) }
        return true
    }

    fun loadAvailability() {
        val s = state.value
        val data = s.data.valueOrNull() ?: return
        if (s.selectedServiceIds.isEmpty()) return
        availabilityJob?.cancel()
        state.update { it.copy(availability = Loadable.Loading, waitlistJoined = false) }
        availabilityJob = viewModelScope.launch {
            val result = loadable {
                getAvailability(data.salon.id, s.selectedServiceIds, s.stylistId, s.today, data.rescheduling?.id)
            }
            state.update { st ->
                val days = result.valueOrNull()?.days.orEmpty()
                val keepDate = st.selectedDate?.takeIf { d -> days.any { it.date == d } }
                val firstWithSlots = days.firstOrNull { it.slots.isNotEmpty() }?.date
                st.copy(availability = result, selectedDate = keepDate ?: firstWithSlots ?: days.firstOrNull()?.date)
            }
        }
    }

    fun selectDate(date: LocalDate) = state.update { it.copy(selectedDate = date, waitlistJoined = false) }

    /** „Prvi slobodan“: prvi slot u narednih 14 dana. */
    fun selectFirstFree() {
        val days = state.value.availability?.valueOrNull()?.days ?: return
        val (date, slot) = firstFreeSlot(days) ?: return
        state.update { it.copy(selectedDate = date) }
        selectSlot(slot)
    }

    /** Izbor slota ga odmah zaključava na 5 minuta (osim kod promene termina, koja je atomska). */
    fun selectSlot(slot: Slot) {
        val s = state.value
        val data = s.data.valueOrNull() ?: return
        if (s.isReschedule) {
            state.update { it.copy(selectedSlot = slot, error = null) }
            return
        }
        if (s.holding) return
        state.update { it.copy(selectedSlot = slot, holding = true, error = null, holdExpired = false) }
        viewModelScope.launch {
            try {
                val hold = holdSlot(data.salon.id, s.selectedServiceIds, s.stylistId, slot.start, s.hold)
                state.update { it.copy(hold = hold, holding = false) }
                startCountdown(hold)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BookingError.SlotTaken) {
                applyRefreshedSlots(slot.start.belgradeDate(), e.slots)
                state.update { it.copy(holding = false, selectedSlot = null, hold = null, error = e) }
            } catch (e: Exception) {
                state.update { it.copy(holding = false, selectedSlot = null, error = e) }
            }
        }
    }

    fun onNote(note: String) = state.update { it.copy(note = note.take(MAX_NOTE)) }

    fun confirm() {
        val s = state.value
        if (s.confirming) return
        val data = s.data.valueOrNull() ?: return
        state.update { it.copy(confirming = true, error = null) }
        viewModelScope.launch {
            try {
                val appointment = if (data.rescheduling != null) {
                    val slot = s.selectedSlot ?: return@launch
                    reschedule(data.rescheduling.id, slot.start, s.stylistId)
                } else {
                    val hold = s.hold ?: return@launch
                    confirmBooking(hold, s.note)
                }
                countdown?.cancel()
                state.update { it.copy(completedAppointmentId = appointment.id, hold = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BookingError.SlotTaken) {
                val date = s.selectedSlot?.start?.belgradeDate()
                if (date != null) applyRefreshedSlots(date, e.slots)
                countdown?.cancel()
                state.update { it.copy(step = BookingStep.Time, hold = null, selectedSlot = null, error = e) }
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(confirming = false) }
            }
        }
    }

    fun joinWaitlist() {
        val s = state.value
        val data = s.data.valueOrNull() ?: return
        val date = s.selectedDate ?: return
        state.update { it.copy(joiningWaitlist = true, error = null) }
        viewModelScope.launch {
            try {
                booking.joinWaitlist(data.salon.id, date, s.selectedServiceIds, s.stylistId)
                state.update { it.copy(waitlistJoined = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(joiningWaitlist = false) }
            }
        }
    }

    fun clearError() = state.update { it.copy(error = null) }

    fun completionHandled() = state.update { it.copy(completedAppointmentId = null) }

    private fun applyRefreshedSlots(date: LocalDate, slots: List<Slot>) {
        state.update { st ->
            val availability = st.availability?.valueOrNull() ?: return@update st
            val days = availability.days.map { if (it.date == date) DaySlots(date, slots) else it }
            st.copy(availability = Loadable.Ready(availability.copy(days = days)))
        }
    }

    private fun startCountdown(hold: Hold) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (isActive) {
                val left = Duration.between(clock.instant(), hold.expiresAt).seconds.coerceAtLeast(0)
                state.update { it.copy(holdSecondsLeft = left) }
                if (left <= 0) {
                    state.update { it.copy(hold = null, holdExpired = true, selectedSlot = null) }
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    private fun releaseCurrentHold() {
        val hold = state.value.hold ?: return
        countdown?.cancel()
        state.update { it.copy(hold = null, selectedSlot = null, holdSecondsLeft = 0) }
        appScope.launch { runCatching { booking.releaseHold(hold.appointmentId) } }
    }

    /** Napuštanje toka oslobađa sačuvan slot (istekao bi i sam za 5 minuta). */
    override fun onCleared() {
        val hold = state.value.hold
        if (hold != null && state.value.completedAppointmentId == null && hold.expiresAt.isAfter(Instant.now(clock))) {
            appScope.launch { runCatching { booking.releaseHold(hold.appointmentId) } }
        }
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val TICK_MS = 1_000L
        const val MAX_NOTE = 500
    }
}
