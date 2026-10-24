package rs.termin.app.feature.appointments

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.isUpcoming
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.ui.Loadable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class AppointmentsLists(
    val upcoming: List<Appointment>,
    val past: List<Appointment>,
    val waitlist: List<WaitlistEntry>,
)

data class AppointmentsUiState(
    val lists: Loadable<AppointmentsLists> = Loadable.Loading,
    val showPast: Boolean = false,
    val selectedId: String? = null,
    val now: Instant = Instant.EPOCH,
    val online: Boolean = true,
)

/** Podela termina na predstojeće (rastuće) i prošle (opadajuće). */
fun splitAppointments(all: List<Appointment>, now: Instant): Pair<List<Appointment>, List<Appointment>> {
    val upcoming = all.filter { isUpcoming(it, now) }.sortedBy { it.start }
    val past = all.filterNot { isUpcoming(it, now) }.sortedByDescending { it.start }
    return upcoming to past
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    auth: AuthRepository,
    appointments: AppointmentRepository,
    private val booking: BookingRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(AppointmentsUiState())
    private val retry = MutableStateFlow(0)

    /** „Sada“ se osvežava svakog minuta zbog odbrojavanja („za 2 sata“). */
    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(MINUTE_MS)
        }
    }

    private val lists: Flow<Loadable<AppointmentsLists>> = combine(auth.authState, retry) { state, _ -> state }
        .flatMapLatest { state ->
            if (state !is AuthState.SignedIn) {
                flowOf(Loadable.Ready(AppointmentsLists(emptyList(), emptyList(), emptyList())))
            } else {
                combine(
                    appointments.observeMine(state.uid),
                    appointments.observeMyWaitlist(state.uid).catch { emit(emptyList()) },
                    ticker,
                ) { mine, waitlist, now ->
                    val (upcoming, past) = splitAppointments(mine, now)
                    val today = now.atZone(BelgradeZone).toLocalDate()
                    val activeWaitlist = waitlist.filter { it.status in setOf("waiting", "notified") && !it.date.isBefore(today) }
                    Loadable.Ready(AppointmentsLists(upcoming, past, activeWaitlist)) as Loadable<AppointmentsLists>
                }.onStart { emit(Loadable.Loading) }.catch { emit(Loadable.Failed(it)) }
            }
        }

    val uiState: StateFlow<AppointmentsUiState> = combine(local, lists, ticker, connectivity.isOnline) { s, l, now, online ->
        s.copy(lists = l, now = now, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun showPast(past: Boolean) = local.update { it.copy(showPast = past) }

    fun select(id: String?) = local.update { it.copy(selectedId = id) }

    fun retry() = retry.update { it + 1 }

    fun leaveWaitlist(id: String) {
        viewModelScope.launch { runCatching { booking.leaveWaitlist(id) } }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MINUTE_MS = 60_000L
    }
}
