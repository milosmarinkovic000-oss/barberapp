package rs.termin.app.feature.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.OfflineBanner
import rs.termin.app.core.designsystem.component.SectionHeader
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.AppointmentCard
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.isWideLayout
import rs.termin.app.feature.calendar.visibleInCalendar
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class StaffFilter { Today, Upcoming, Past }

data class StaffAppointmentsUiState(
    val session: StaffSession? = null,
    val filter: StaffFilter = StaffFilter.Today,
    val appointments: Loadable<List<Appointment>> = Loadable.Loading,
    val selectedId: String? = null,
    val now: Instant = Instant.EPOCH,
    val online: Boolean = true,
)

/** Vremenski opseg filtera (Beograd): danas, narednih 14 dana ili poslednjih 30 dana. */
fun staffRange(filter: StaffFilter, today: LocalDate): Pair<Instant, Instant> {
    fun start(d: LocalDate) = d.atStartOfDay(BelgradeZone).toInstant()
    return when (filter) {
        StaffFilter.Today -> start(today) to start(today.plusDays(1))
        StaffFilter.Upcoming -> start(today.plusDays(1)) to start(today.plusDays(UPCOMING_DAYS))
        StaffFilter.Past -> start(today.minusDays(PAST_DAYS)) to start(today)
    }
}

private const val UPCOMING_DAYS = 15L
private const val PAST_DAYS = 30L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StaffAppointmentsViewModel @Inject constructor(
    auth: AuthRepository,
    appointments: AppointmentRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(StaffAppointmentsUiState())
    private val retry = MutableStateFlow(0)
    private val session = auth.authState.map { it.staffSession() }.distinctUntilChanged()

    private val list = combine(session, local.map { it.filter }.distinctUntilChanged(), retry) { s, f, _ -> s to f }
        .flatMapLatest { (s, filter) ->
            if (s == null) return@flatMapLatest flowOf(Loadable.Loading)
            val (from, to) = staffRange(filter, LocalDate.now(clock.withZone(BelgradeZone)))
            appointments.observeSalonAppointments(s.salonId, from, to, s.stylistFilter)
                .map { all ->
                    val now = clock.instant()
                    val visible = all.filter { filter == StaffFilter.Past || visibleInCalendar(it, now) }
                    Loadable.Ready(if (filter == StaffFilter.Past) visible.sortedByDescending { it.start } else visible) as Loadable<List<Appointment>>
                }
                .onStart { emit(Loadable.Loading) }
                .catch { emit(Loadable.Failed(it)) }
        }

    val uiState: StateFlow<StaffAppointmentsUiState> = combine(local, session, list, connectivity.isOnline) { l, s, a, online ->
        l.copy(session = s, appointments = a, online = online, now = clock.instant())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun setFilter(filter: StaffFilter) = local.update { it.copy(filter = filter) }

    fun select(id: String?) = local.update { it.copy(selectedId = id) }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun StaffAppointmentsScreen(
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    detailPane: @Composable (String) -> Unit,
    viewModel: StaffAppointmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wide = isWideLayout()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    Row(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(Modifier.weight(if (wide) 0.42f else 1f).fillMaxHeight()) {
            if (!state.online) OfflineBanner()
            Text(
                stringResource(R.string.staff_appointments_title),
                style = TerminTheme.type.title1,
                color = colors.text,
                modifier = Modifier
                    .padding(horizontal = spacing.gutter)
                    .padding(top = spacing.lg, bottom = spacing.sm)
                    .semantics { heading() },
            )
            SegmentedControl(
                options = StaffFilter.entries,
                selected = state.filter,
                label = {
                    when (it) {
                        StaffFilter.Today -> stringResource(R.string.staff_filter_today)
                        StaffFilter.Upcoming -> stringResource(R.string.staff_filter_upcoming)
                        StaffFilter.Past -> stringResource(R.string.staff_filter_past)
                    }
                },
                onSelect = viewModel::setFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter),
            )
            when (val list = state.appointments) {
                Loadable.Loading -> LoadingSkeleton(Modifier.padding(spacing.gutter)) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(88.dp), TerminTheme.radii.cardShape) }
                    }
                }
                is Loadable.Failed -> ErrorState(viewModel::retry)
                is Loadable.Ready -> if (list.value.isEmpty()) {
                    EmptyState(
                        Illustration.Calendar,
                        stringResource(R.string.staff_appointments_title),
                        stringResource(
                            when (state.filter) {
                                StaffFilter.Today -> R.string.staff_empty_today
                                StaffFilter.Upcoming -> R.string.staff_empty_upcoming
                                StaffFilter.Past -> R.string.staff_empty_past
                            },
                        ),
                    )
                } else {
                    val showStylist = state.session?.isOwner == true
                    val groups = list.value.groupBy { f.date(it.start) }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = spacing.sm, bottom = contentPadding.calculateBottomPadding() + spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        groups.forEach { (date, items) ->
                            if (state.filter != StaffFilter.Today) {
                                item(key = "h-$date", contentType = "header") {
                                    SectionHeader(f.dayLong(date), subtitle = f.price(items.sumOf { it.totalPrice }))
                                }
                            }
                            items(items, key = { it.id }, contentType = { "appointment" }) { a ->
                                AppointmentCard(
                                    appointment = a,
                                    now = state.now,
                                    onClick = { if (wide) viewModel.select(a.id) else onOpen(a.id) },
                                    title = if (showStylist) "${a.customerName} · ${a.stylistName}" else a.customerName,
                                    selected = a.id == state.selectedId,
                                    modifier = Modifier
                                        .padding(horizontal = spacing.gutter)
                                        .fillMaxWidth()
                                        .animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (wide) {
            VerticalDivider(color = colors.divider)
            Box(Modifier.weight(0.58f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val selected = state.selectedId
                if (selected == null) {
                    EmptyState(Illustration.Calendar, stringResource(R.string.staff_appointments_title), stringResource(R.string.appointments_select))
                } else {
                    detailPane(selected)
                }
            }
        }
    }
}
