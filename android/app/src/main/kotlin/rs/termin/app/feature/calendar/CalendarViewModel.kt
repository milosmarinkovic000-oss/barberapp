package rs.termin.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Loadable
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class CalendarMode { Day, Week }

data class CalendarData(
    val salon: Salon,
    /** Kolone (dnevni prikaz) — vlasnik vidi sve aktivne frizere, frizer samo sebe. */
    val stylists: List<Stylist>,
    val appointments: List<Appointment>,
    val timeOff: List<TimeOff>,
)

data class CalendarUiState(
    val session: StaffSession? = null,
    val mode: CalendarMode = CalendarMode.Day,
    val date: LocalDate = LocalDate.MIN,
    /** Nedeljni prikaz za vlasnika: jedan frizer ili svi (null). */
    val stylistFilter: String? = null,
    val data: Loadable<CalendarData> = Loadable.Loading,
    val now: Instant = Instant.EPOCH,
    val online: Boolean = true,
) {
    val weekStart: LocalDate get() = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days: List<LocalDate> get() = if (mode == CalendarMode.Day) listOf(date) else (0L..6L).map { weekStart.plusDays(it) }
}

/** Vidljivi termini: bez otkazanih i isteklih zadržavanja. */
fun visibleInCalendar(appointment: Appointment, now: Instant): Boolean = when (appointment.status) {
    AppointmentStatus.CanceledByCustomer, AppointmentStatus.CanceledBySalon -> false
    AppointmentStatus.Held -> appointment.holdExpiresAt?.isAfter(now) == true
    else -> true
}

/** Opseg sati za mrežu dnevnog prikaza: od najranijeg otvaranja do najkasnijeg zatvaranja (uz termine van toga). */
fun hourRange(salon: Salon, stylists: List<Stylist>, date: LocalDate, appointments: List<Appointment>): IntRange {
    val day = date.dayOfWeek
    val ranges: List<TimeRange> = salon.workingHours.on(day) + stylists.flatMap { it.workingHours?.on(day).orEmpty() }
    var start = ranges.minOfOrNull { it.start.hour } ?: DEFAULT_START
    var end = ranges.maxOfOrNull { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour } ?: DEFAULT_END
    appointments.forEach { a ->
        val s = a.start.atZone(BelgradeZone).toLocalTime()
        val e = a.end.atZone(BelgradeZone).toLocalTime()
        start = minOf(start, s.hour)
        end = maxOf(end, if (e == LocalTime.MIDNIGHT) HOURS_IN_DAY else e.hour + if (e.minute > 0) 1 else 0)
    }
    return start until end.coerceAtLeast(start + 1)
}

private const val DEFAULT_START = 8
private const val DEFAULT_END = 20
private const val HOURS_IN_DAY = 24

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    auth: AuthRepository,
    private val salons: SalonRepository,
    private val appointments: AppointmentRepository,
    private val staff: StaffRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(CalendarUiState(date = LocalDate.now(clock.withZone(BelgradeZone)), now = clock.instant()))
    private val retry = MutableStateFlow(0)

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(MINUTE_MS)
        }
    }

    private val session = auth.authState.map { it.staffSession() }.distinctUntilChanged()

    private val query = combine(session, local, retry) { s, l, _ -> Triple(s, l.days, l.stylistFilter.takeIf { l.mode == CalendarMode.Week }) }
        .distinctUntilChanged()

    private val data: Flow<Loadable<CalendarData>> = query.flatMapLatest { (s, days, weekFilter) ->
        if (s == null) return@flatMapLatest flowOf(Loadable.Loading)
        val from = days.first().atStartOfDay(BelgradeZone).toInstant()
        val to = days.last().plusDays(1).atStartOfDay(BelgradeZone).toInstant()
        val stylistId = s.stylistFilter ?: weekFilter
        val stylists = salons.observeStylists(s.salonId).map { list ->
            list.filter { it.active && (s.stylistFilter == null || it.id == s.stylistFilter) }
        }
        combine(
            salons.observeSalon(s.salonId),
            stylists,
            appointments.observeSalonAppointments(s.salonId, from, to, stylistId),
            stylists.flatMapLatest { list -> timeOffFor(s.salonId, list.map { it.id }, from, to) },
        ) { salon, list, appts, off ->
            if (salon == null) Loadable.Failed(NoSuchElementException(s.salonId)) else Loadable.Ready(CalendarData(salon, list, appts, off))
        }.onStart { emit(Loadable.Loading) }.catch { emit(Loadable.Failed(it)) }
    }

    private fun timeOffFor(salonId: String, ids: List<String>, from: Instant, to: Instant): Flow<List<TimeOff>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        val flows = ids.map { id -> staff.observeTimeOff(salonId, id).catch { emit(emptyList()) } }
        return combine(flows) { lists -> lists.flatMap { it }.filter { it.end.isAfter(from) && it.start.isBefore(to) } }
    }

    val uiState: StateFlow<CalendarUiState> = combine(local, session, data, ticker, connectivity.isOnline) { l, s, d, now, online ->
        l.copy(session = s, data = d, now = now, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun setMode(mode: CalendarMode) = local.update { it.copy(mode = mode) }

    fun previous() = local.update { it.copy(date = it.date.minusDays(if (it.mode == CalendarMode.Day) 1 else 7)) }

    fun next() = local.update { it.copy(date = it.date.plusDays(if (it.mode == CalendarMode.Day) 1 else 7)) }

    fun today() = local.update { it.copy(date = LocalDate.now(clock.withZone(BelgradeZone))) }

    fun selectDate(date: LocalDate) = local.update { it.copy(date = date, mode = CalendarMode.Day) }

    fun filterStylist(id: String?) = local.update { it.copy(stylistFilter = id) }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MINUTE_MS = 60_000L
    }
}
