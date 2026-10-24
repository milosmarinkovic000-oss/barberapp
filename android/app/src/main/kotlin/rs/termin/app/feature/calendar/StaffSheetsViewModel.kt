package rs.termin.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ManualBooking
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.totalsOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private suspend fun <T> guarded(onError: (Exception) -> Unit, block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
    null
}

// ---------------- Ručni unos termina ----------------

data class ManualBookingUiState(
    val session: StaffSession? = null,
    val services: List<Service> = emptyList(),
    val stylists: List<Stylist> = emptyList(),
    val stylistId: String? = null,
    val serviceIds: Set<String> = emptySet(),
    val date: LocalDate = LocalDate.MIN,
    val walkIn: Boolean = false,
    val slots: List<Slot>? = null,
    val loadingSlots: Boolean = false,
    val start: Instant? = null,
    val name: String = "",
    val phone: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val showErrors: Boolean = false,
    val savedId: String? = null,
    val error: Throwable? = null,
) {
    val selectedServices: List<Service> get() = services.filter { it.id in serviceIds }
    val availableServices: List<Service> get() = services.filter { it.active && (stylistId == null || stylistId in it.stylistIds) }
    val phoneE164: String? get() = phone.takeIf { it.isNotBlank() }?.let(PhoneNumbers::toE164)
    val phoneValid: Boolean get() = phone.isBlank() || phoneE164 != null
    val valid: Boolean get() = name.trim().length >= 2 && serviceIds.isNotEmpty() && stylistId != null && phoneValid && (walkIn || start != null)
}

@HiltViewModel
class ManualBookingViewModel @Inject constructor(
    private val salons: SalonRepository,
    private val booking: BookingRepository,
    private val staff: StaffRepository,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(ManualBookingUiState())
    val uiState: StateFlow<ManualBookingUiState> = state.asStateFlow()
    private var slotsJob: Job? = null

    /** Priprema formu; [start] je vreme na koje je osoblje dodirnulo u kalendaru. */
    fun prepare(session: StaffSession, stylistId: String?, date: LocalDate, start: Instant?) {
        if (state.value.session != null) return
        state.update { it.copy(session = session, stylistId = stylistId ?: session.stylistFilter, date = date, start = start) }
        viewModelScope.launch {
            guarded({ e -> state.update { it.copy(error = e) } }) {
                val services = salons.observeServices(session.salonId).first()
                val stylists = salons.observeStylists(session.salonId).first()
                    .filter { it.active && session.canManageStylist(it.id) }
                state.update { s ->
                    s.copy(services = services, stylists = stylists, stylistId = s.stylistId ?: stylists.singleOrNull()?.id)
                }
            }
        }
    }

    fun selectStylist(id: String) {
        state.update { s ->
            val allowed = s.services.filter { id in it.stylistIds }.map { it.id }.toSet()
            s.copy(stylistId = id, serviceIds = s.serviceIds.intersect(allowed))
        }
        refreshSlots()
    }

    fun toggleService(id: String) {
        state.update { s -> s.copy(serviceIds = if (id in s.serviceIds) s.serviceIds - id else s.serviceIds + id) }
        refreshSlots()
    }

    fun selectDate(date: LocalDate) {
        state.update { it.copy(date = date, start = null) }
        refreshSlots()
    }

    fun setWalkIn(walkIn: Boolean) {
        state.update { it.copy(walkIn = walkIn) }
        if (!walkIn) refreshSlots()
    }

    fun selectSlot(start: Instant) = state.update { it.copy(start = start) }

    fun onName(value: String) = state.update { it.copy(name = value.take(MAX_NAME)) }

    fun onPhone(value: String) = state.update { it.copy(phone = value.take(MAX_PHONE)) }

    fun onNote(value: String) = state.update { it.copy(note = value.take(MAX_NOTE)) }

    private fun refreshSlots() {
        val s = state.value
        val session = s.session ?: return
        val stylist = s.stylistId
        if (s.walkIn || stylist == null || s.serviceIds.isEmpty()) {
            state.update { it.copy(slots = null) }
            return
        }
        slotsJob?.cancel()
        state.update { it.copy(loadingSlots = true) }
        slotsJob = viewModelScope.launch {
            val availability = guarded({ e -> state.update { it.copy(error = e) } }) {
                booking.availability(session.salonId, s.serviceIds.toList(), stylist, s.date, 1)
            }
            val slots = availability?.days?.firstOrNull { it.date == s.date }?.slots.orEmpty()
            state.update { cur ->
                cur.copy(slots = slots, loadingSlots = false, start = cur.start?.takeIf { start -> slots.any { it.start == start } })
            }
        }
    }

    fun save() {
        val s = state.value
        val session = s.session ?: return
        if (!s.valid) {
            state.update { it.copy(showErrors = true) }
            return
        }
        val stylist = s.stylistId ?: return
        val start = if (s.walkIn) roundDown(clock.instant()) else s.start ?: return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val created = guarded({ e ->
                state.update { it.copy(error = e) }
                if (e is BookingError.SlotTaken) refreshSlots()
            }) {
                staff.createManualBooking(
                    ManualBooking(
                        salonId = session.salonId,
                        stylistId = stylist,
                        serviceIds = s.selectedServices.map { it.id },
                        start = start,
                        customerName = s.name.trim(),
                        customerPhone = s.phoneE164,
                        note = s.note.trim(),
                        walkIn = s.walkIn,
                    ),
                )
            }
            state.update { it.copy(saving = false, savedId = created?.id) }
        }
    }

    /** Klijent bez najave: početak se zaokružuje na prethodnih 5 minuta. */
    private fun roundDown(instant: Instant): Instant {
        val local = instant.atZone(BelgradeZone).truncatedTo(ChronoUnit.MINUTES)
        return local.withMinute(local.minute - local.minute % WALK_IN_ROUND).toInstant()
    }

    private companion object {
        const val MAX_NAME = 60
        const val MAX_PHONE = 20
        const val MAX_NOTE = 300
        const val WALK_IN_ROUND = 5
    }
}

/** Ukupno trajanje i cena izabranih usluga (za prikaz u formi). */
fun ManualBookingUiState.totals() = totalsOf(selectedServices)

// ---------------- Blokiranje vremena ----------------

data class BlockTimeUiState(
    val session: StaffSession? = null,
    val stylists: List<Stylist> = emptyList(),
    val stylistId: String? = null,
    val kind: TimeOffKind = TimeOffKind.Block,
    /** Najraniji dan koji može da se blokira. */
    val today: LocalDate = LocalDate.MIN,
    val date: LocalDate = LocalDate.MIN,
    val allDay: Boolean = false,
    val from: LocalTime = LocalTime.of(DEFAULT_FROM, 0),
    val to: LocalTime = LocalTime.of(DEFAULT_FROM + 1, 0),
    val days: Int = 1,
    val reason: String = "",
    val saving: Boolean = false,
    val result: BlockOutcome? = null,
    val error: Throwable? = null,
) {
    val range: Pair<Instant, Instant>
        get() = if (allDay) {
            date.atStartOfDay(BelgradeZone).toInstant() to date.plusDays(days.toLong()).atStartOfDay(BelgradeZone).toInstant()
        } else {
            date.atTime(from).atZone(BelgradeZone).toInstant() to date.atTime(to).atZone(BelgradeZone).toInstant()
        }
    val rangeValid: Boolean get() = range.let { (a, b) -> b.isAfter(a) && Duration.between(a, b) <= MAX_RANGE }
    val valid: Boolean get() = stylistId != null && rangeValid

    companion object {
        const val DEFAULT_FROM = 12
        val MAX_RANGE: Duration = Duration.ofDays(60)
    }
}

data class BlockOutcome(val conflicts: Int)

@HiltViewModel
class BlockTimeViewModel @Inject constructor(
    private val salons: SalonRepository,
    private val staff: StaffRepository,
    clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(BlockTimeUiState(today = LocalDate.now(clock.withZone(BelgradeZone))))
    val uiState: StateFlow<BlockTimeUiState> = state.asStateFlow()

    fun prepare(session: StaffSession, stylistId: String?, date: LocalDate, from: LocalTime?) {
        if (state.value.session != null) return
        state.update {
            it.copy(
                session = session,
                stylistId = stylistId ?: session.stylistFilter,
                date = date,
                from = from ?: it.from,
                to = (from ?: it.from).plusHours(1),
            )
        }
        viewModelScope.launch {
            guarded({ e -> state.update { it.copy(error = e) } }) {
                val stylists = salons.observeStylists(session.salonId).first().filter { it.active && session.canManageStylist(it.id) }
                state.update { s -> s.copy(stylists = stylists, stylistId = s.stylistId ?: stylists.singleOrNull()?.id) }
            }
        }
    }

    fun selectStylist(id: String) = state.update { it.copy(stylistId = id) }

    fun selectKind(kind: TimeOffKind) = state.update {
        it.copy(kind = kind, allDay = if (kind == TimeOffKind.Vacation || kind == TimeOffKind.Sick) true else it.allDay)
    }

    fun setDate(date: LocalDate) = state.update { it.copy(date = date) }

    fun setAllDay(allDay: Boolean) = state.update { it.copy(allDay = allDay) }

    fun setFrom(time: LocalTime) = state.update { s -> s.copy(from = time, to = if (s.to <= time) time.plusMinutes(MIN_BLOCK) else s.to) }

    fun setTo(time: LocalTime) = state.update { it.copy(to = time) }

    fun setDays(days: Int) = state.update { it.copy(days = days.coerceIn(1, MAX_DAYS)) }

    fun onReason(value: String) = state.update { it.copy(reason = value.take(MAX_REASON)) }

    fun save() {
        val s = state.value
        val session = s.session ?: return
        val stylist = s.stylistId ?: return
        if (!s.valid || s.saving) return
        val (start, end) = s.range
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = guarded({ e -> state.update { it.copy(error = e) } }) {
                staff.blockTime(session.salonId, stylist, start, end, s.kind, s.reason.trim())
            }
            state.update { it.copy(saving = false, result = result?.let { r -> BlockOutcome(r.conflicts) }) }
        }
    }

    private companion object {
        const val MIN_BLOCK = 15L
        const val MAX_DAYS = 60
        const val MAX_REASON = 200
    }
}
