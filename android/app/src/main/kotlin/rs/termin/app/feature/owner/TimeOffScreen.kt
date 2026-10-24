package rs.termin.app.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDialog
import rs.termin.app.core.designsystem.component.TerminExtendedFab
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Formatter
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.calendar.BlockTimeSheet
import rs.termin.app.feature.calendar.timeOffKindLabel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class TimeOffUiState(
    val session: StaffSession? = null,
    val items: Loadable<List<TimeOff>> = Loadable.Loading,
    val now: Instant = Instant.EPOCH,
    val error: Throwable? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TimeOffViewModel.Factory::class)
class TimeOffViewModel @AssistedInject constructor(
    @Assisted private val stylistId: String,
    auth: AuthRepository,
    private val staff: StaffRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(stylistId: String): TimeOffViewModel
    }

    private val retry = MutableStateFlow(0)
    private val error = MutableStateFlow<Throwable?>(null)
    private val session = auth.authState.map { it.staffSession() }.distinctUntilChanged()

    private val items = combine(session, retry) { s, _ -> s }.flatMapLatest { s ->
        if (s == null) {
            flowOf(Loadable.Loading)
        } else {
            staff.observeTimeOff(s.salonId, stylistId)
                .map<List<TimeOff>, Loadable<List<TimeOff>>> { Loadable.Ready(it) }
                .onStart { emit(Loadable.Loading) }
                .catch { emit(Loadable.Failed(it)) }
        }
    }

    val uiState: StateFlow<TimeOffUiState> = combine(session, items, error) { s, i, e -> TimeOffUiState(s, i, clock.instant(), e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), TimeOffUiState())

    fun remove(id: String) {
        val salonId = uiState.value.session?.salonId ?: return
        error.value = null
        viewModelScope.launch {
            runCatching { staff.removeTimeOff(salonId, stylistId, id) }.onFailure { e -> error.value = e }
        }
    }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

/** „1. maj, 12:00–13:00“ ili „1.–14. maj“ (ceo dan). */
fun timeOffRange(f: Formatter, off: TimeOff): String {
    val start = off.start.atZone(BelgradeZone)
    val end = off.end.atZone(BelgradeZone)
    val wholeDays = start.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalTime() == LocalTime.MIDNIGHT
    return if (wholeDays) {
        val last = end.toLocalDate().minusDays(1)
        if (last == start.toLocalDate()) f.dayLong(last) else "${f.dayMonth(start.toLocalDate())} – ${f.dayMonth(last)}"
    } else {
        "${f.dayLong(start.toLocalDate())}, ${f.timeRange(off.start, off.end)}"
    }
}

@Composable
fun TimeOffScreen(stylistId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<TimeOffViewModel, TimeOffViewModel.Factory>(key = "timeoff-$stylistId") { it.create(stylistId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    var sheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var counter by rememberSaveable { mutableStateOf(0) }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.time_off_title), onBack = onBack) },
        floatingActionButton = {
            if (state.session != null) {
                TerminExtendedFab(stringResource(R.string.time_off_add), TerminIcon.Add, {
                        counter += 1
                        sheetKey = "timeoff-sheet-$counter"
                })
            }
        },
    ) { padding ->
        when (val items = state.items) {
            Loadable.Loading -> LoadingSkeleton(Modifier.padding(padding).padding(spacing.gutter)) { SkeletonLines(4) }
            is Loadable.Failed -> ErrorState(viewModel::retry, Modifier.padding(padding))
            is Loadable.Ready -> if (items.value.isEmpty()) {
                EmptyState(Illustration.Calendar, stringResource(R.string.time_off_title), stringResource(R.string.time_off_empty), Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.gutter,
                        end = spacing.gutter,
                        top = padding.calculateTopPadding() + spacing.sm,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.error?.let { e -> item(key = "error") { Notice(stringResource(e.messageRes()), tone = NoticeTone.Error) } }
                    items(items.value, key = { it.id }) { off ->
                        val past = !off.end.isAfter(state.now)
                        TerminCard(Modifier.fillMaxWidth().animateItem()) {
                            TerminListItem(
                                timeOffRange(f, off),
                                subtitle = listOfNotNull(
                                    timeOffKindLabel(off.kind),
                                    off.reason.takeIf { it.isNotBlank() },
                                    stringResource(R.string.time_off_past).takeIf { past },
                                ).joinToString(" · "),
                                leadingIcon = TerminIcon.EventBusy,
                                titleColor = if (past) colors.textSecondary else colors.text,
                                trailing = if (past) {
                                    null
                                } else {
                                    { TerminIconButton(TerminIcon.Delete, stringResource(R.string.time_off_remove), { confirmRemove = off.id }) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    val session = state.session
    val key = sheetKey
    if (key != null && session != null) {
        BlockTimeSheet(
            session = session,
            stylistId = stylistId,
            date = LocalDate.now(BelgradeZone),
            from = null,
            key = key,
            onDismiss = { sheetKey = null },
        )
    }
    confirmRemove?.let { id ->
        TerminDialog(
            title = stringResource(R.string.time_off_remove_title),
            text = stringResource(R.string.time_off_remove_text),
            confirmLabel = stringResource(R.string.time_off_remove),
            onConfirm = {
                confirmRemove = null
                viewModel.remove(id)
            },
            onDismiss = { confirmRemove = null },
        )
    }
}
