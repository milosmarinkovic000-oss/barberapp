package rs.termin.app.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentSource
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.DateField
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.StatusPill
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class RescheduleState(
    val date: LocalDate,
    val slots: List<Slot>? = null,
    val loading: Boolean = false,
    val selected: Instant? = null,
    val saving: Boolean = false,
)

data class StaffDetailUiState(
    val appointment: Loadable<Appointment?> = Loadable.Loading,
    val history: List<Appointment>? = null,
    val busy: Boolean = false,
    val confirmCancel: Boolean = false,
    val cancelReason: String = "",
    val reschedule: RescheduleState? = null,
    val message: Int? = null,
    val error: Throwable? = null,
    val now: Instant = Instant.EPOCH,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = StaffAppointmentDetailViewModel.Factory::class)
class StaffAppointmentDetailViewModel @AssistedInject constructor(
    @Assisted private val appointmentId: String,
    private val appointments: AppointmentRepository,
    private val booking: BookingRepository,
    private val staff: StaffRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(appointmentId: String): StaffAppointmentDetailViewModel
    }

    private val local = MutableStateFlow(StaffDetailUiState())
    private val retry = MutableStateFlow(0)
    private var slotsJob: Job? = null

    private val appointment = retry.flatMapLatest {
        appointments.observeAppointment(appointmentId)
            .map<Appointment?, Loadable<Appointment?>> { Loadable.Ready(it) }
            .onStart { emit(Loadable.Loading) }
            .catch { emit(Loadable.Failed(it)) }
    }

    val uiState: StateFlow<StaffDetailUiState> = combine(local, appointment) { s, a ->
        s.copy(appointment = a, now = clock.instant())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    init {
        // Istorija klijenta u ovom salonu (jednom, posle prvog učitavanja termina).
        viewModelScope.launch {
            val list = runCatching {
                val a = appointments.observeAppointment(appointmentId).filterNotNull().first()
                appointments.customerHistory(a.salonId, a.customerUid, a.customerPhone).filter { it.id != a.id }
            }.getOrDefault(emptyList())
            local.update { it.copy(history = list) }
        }
    }

    private fun run(success: Int?, block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                block()
                local.update { it.copy(message = success) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    fun setStatus(status: AppointmentStatus) = run(R.string.staff_status_updated) { staff.updateStatus(appointmentId, status) }

    fun requestCancel() = local.update { it.copy(confirmCancel = true) }

    fun dismissCancel() = local.update { it.copy(confirmCancel = false, cancelReason = "") }

    fun onCancelReason(value: String) = local.update { it.copy(cancelReason = value.take(MAX_REASON)) }

    fun cancel() {
        val reason = local.value.cancelReason.trim()
        local.update { it.copy(confirmCancel = false) }
        run(R.string.appointment_canceled) { booking.cancel(appointmentId, reason) }
    }

    fun startReschedule() {
        val a = uiState.value.appointment.valueOrNull() ?: return
        local.update { it.copy(reschedule = RescheduleState(date = a.start.atZone(BelgradeZone).toLocalDate())) }
        loadSlots()
    }

    fun dismissReschedule() = local.update { it.copy(reschedule = null) }

    fun rescheduleDate(date: LocalDate) {
        local.update { s -> s.copy(reschedule = s.reschedule?.copy(date = date, selected = null)) }
        loadSlots()
    }

    fun rescheduleSlot(start: Instant) = local.update { s -> s.copy(reschedule = s.reschedule?.copy(selected = start)) }

    private fun loadSlots() {
        val a = uiState.value.appointment.valueOrNull() ?: return
        val r = local.value.reschedule ?: return
        slotsJob?.cancel()
        local.update { s -> s.copy(reschedule = s.reschedule?.copy(loading = true)) }
        slotsJob = viewModelScope.launch {
            val result = runCatching {
                booking.availability(a.salonId, a.services.map { it.id }, a.stylistId, r.date, 1, excludeAppointmentId = a.id)
            }
            val slots = result.getOrNull()?.days?.firstOrNull { it.date == r.date }?.slots.orEmpty()
            local.update { s -> s.copy(reschedule = s.reschedule?.copy(slots = slots, loading = false), error = result.exceptionOrNull()) }
        }
    }

    fun confirmReschedule() {
        val a = uiState.value.appointment.valueOrNull() ?: return
        val start = local.value.reschedule?.selected ?: return
        local.update { s -> s.copy(reschedule = s.reschedule?.copy(saving = true), error = null) }
        viewModelScope.launch {
            try {
                booking.reschedule(a.id, start, a.stylistId)
                local.update { it.copy(reschedule = null, message = R.string.staff_status_updated) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { s -> s.copy(reschedule = s.reschedule?.copy(saving = false), error = e) }
                if (e is BookingError.SlotTaken) loadSlots()
            }
        }
    }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MAX_REASON = 200
    }
}

/**
 * Prelazi statusa koje osoblje sme da uradi (isto kao STAFF_TRANSITIONS na serveru): potvrda
 * zadržanog termina, ishod posle početka i ispravka pogrešno označenog ishoda.
 */
fun staffActions(status: AppointmentStatus, started: Boolean): List<AppointmentStatus> = when (status) {
    AppointmentStatus.Held -> listOf(AppointmentStatus.Confirmed)
    AppointmentStatus.Confirmed -> if (started) listOf(AppointmentStatus.Completed, AppointmentStatus.NoShow) else emptyList()
    AppointmentStatus.Completed -> listOf(AppointmentStatus.NoShow, AppointmentStatus.Confirmed)
    AppointmentStatus.NoShow -> listOf(AppointmentStatus.Completed, AppointmentStatus.Confirmed)
    AppointmentStatus.CanceledByCustomer, AppointmentStatus.CanceledBySalon -> emptyList()
}

private fun actionLabel(from: AppointmentStatus, to: AppointmentStatus): Int = when (to) {
    AppointmentStatus.Completed -> R.string.staff_mark_completed
    AppointmentStatus.NoShow -> R.string.staff_mark_no_show
    AppointmentStatus.Confirmed -> if (from == AppointmentStatus.Held) R.string.staff_confirm else R.string.staff_reopen
    else -> R.string.staff_cancel
}

private fun actionIcon(to: AppointmentStatus): TerminIcon = when (to) {
    AppointmentStatus.Completed -> TerminIcon.TaskAlt
    AppointmentStatus.NoShow -> TerminIcon.PersonOff
    AppointmentStatus.Confirmed -> TerminIcon.CheckCircle
    else -> TerminIcon.EventBusy
}

@Composable
fun sourceLabel(source: AppointmentSource): String = stringResource(
    when (source) {
        AppointmentSource.App -> R.string.staff_source_app
        AppointmentSource.Manual -> R.string.staff_source_manual
        AppointmentSource.Calendly -> R.string.staff_source_calendly
        AppointmentSource.Google -> R.string.staff_source_google
        AppointmentSource.Calcom -> R.string.staff_source_calcom
    },
)

@Composable
fun StaffAppointmentDetailScreen(appointmentId: String, onBack: (() -> Unit)?) {
    val viewModel = hiltViewModel<StaffAppointmentDetailViewModel, StaffAppointmentDetailViewModel.Factory>(key = "staff-$appointmentId") {
        it.create(appointmentId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val f = formatter
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.appointment_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val loaded = state.appointment) {
                Loadable.Loading -> LoadingSkeleton(Modifier.padding(spacing.gutter)) { SkeletonLines(6) }
                is Loadable.Failed -> ErrorState(viewModel::retry)
                is Loadable.Ready -> {
                    val a = loaded.value
                    if (a == null) {
                        EmptyState(Illustration.Calendar, stringResource(R.string.appointment_not_found), stringResource(R.string.appointments_select))
                    } else {
                        Column(
                            Modifier
                                .readableWidth()
                                .fillMaxWidth()
                                .padding(horizontal = spacing.gutter, vertical = spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            StatusPill(a.status)
                            Text(a.customerName.ifBlank { stringResource(R.string.staff_customer_walk_in) }, style = TerminTheme.type.title1, color = colors.text, modifier = Modifier.semantics { heading() })
                            Text("${f.dateTime(a.start)} · ${f.timeRange(a.start, a.end)}", style = TerminTheme.type.bodyStrong, color = colors.accentText)
                            state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
                            state.message?.let { Notice(stringResource(it), tone = NoticeTone.Success) }

                            TerminCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    TerminListItem(a.stylistName, subtitle = stringResource(R.string.appointment_stylist), leadingIcon = TerminIcon.ContentCut)
                                    TerminDivider()
                                    val phone = a.customerPhone
                                    TerminListItem(
                                        phone?.let(PhoneNumbers::display) ?: "—",
                                        subtitle = stringResource(R.string.staff_detail_customer),
                                        leadingIcon = TerminIcon.Call,
                                        onClick = phone?.let { p -> { Actions.call(context, p) } },
                                        showChevron = phone != null,
                                    )
                                    TerminDivider()
                                    TerminListItem(sourceLabel(a.source), subtitle = stringResource(R.string.staff_detail_source), leadingIcon = TerminIcon.Link)
                                }
                            }
                            TerminCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Overline(stringResource(R.string.appointment_services))
                                    a.services.forEach { s ->
                                        Row {
                                            Text("${s.name} · ${f.duration(s.durationMin)}", style = TerminTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
                                            Text(f.price(s.price), style = TerminTheme.type.price, color = colors.text)
                                        }
                                    }
                                    TerminDivider()
                                    Row {
                                        Text(stringResource(R.string.appointment_total), style = TerminTheme.type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                                        Text(f.price(a.totalPrice), style = TerminTheme.type.price, color = colors.text)
                                    }
                                    if (a.note.isNotBlank()) {
                                        TerminDivider()
                                        Overline(stringResource(R.string.appointment_note))
                                        Text(a.note, style = TerminTheme.type.body, color = colors.text)
                                    }
                                }
                            }

                            val started = !a.start.isAfter(state.now)
                            val actions = staffActions(a.status, started)
                            if (actions.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                    actions.forEachIndexed { index, target ->
                                        TerminButton(
                                            stringResource(actionLabel(a.status, target)),
                                            { viewModel.setStatus(target) },
                                            variant = if (index == 0) ButtonVariant.Primary else ButtonVariant.Secondary,
                                            icon = actionIcon(target),
                                            loading = state.busy && index == 0,
                                            enabled = !state.busy,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            if (a.status == AppointmentStatus.Confirmed && !started) {
                                TerminButton(stringResource(R.string.staff_reschedule), viewModel::startReschedule, variant = ButtonVariant.Secondary, icon = TerminIcon.Schedule, fullWidth = true)
                            }
                            if (a.status.isActive) {
                                TerminButton(stringResource(R.string.staff_cancel), viewModel::requestCancel, variant = ButtonVariant.Danger, icon = TerminIcon.EventBusy, enabled = !state.busy, fullWidth = true)
                            }

                            Overline(stringResource(R.string.staff_history))
                            val history = state.history
                            when {
                                history == null -> SkeletonLines(2)
                                history.isEmpty() -> Text(stringResource(R.string.staff_history_empty), style = TerminTheme.type.callout, color = colors.textSecondary)
                                else -> {
                                    val last = history.firstOrNull { it.start.isBefore(a.start) } ?: history.first()
                                    Text(pluralStringResource(R.plurals.staff_history_count, history.size, history.size, f.dayLong(f.date(last.start))), style = TerminTheme.type.callout, color = colors.textSecondary)
                                    TerminCard(Modifier.fillMaxWidth()) {
                                        history.take(HISTORY_SHOWN).forEachIndexed { i, h ->
                                            if (i > 0) TerminDivider()
                                            TerminListItem(
                                                h.services.joinToString(", ") { it.name },
                                                subtitle = "${f.dayLong(f.date(h.start))} · ${h.stylistName} · ${f.status(h.status)}",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmCancel) {
        TerminBottomSheet(viewModel::dismissCancel) {
            Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(stringResource(R.string.staff_cancel_title), style = TerminTheme.type.title2, color = colors.text, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.staff_cancel_text), style = TerminTheme.type.body, color = colors.textSecondary)
                TerminTextField(state.cancelReason, viewModel::onCancelReason, stringResource(R.string.staff_cancel_reason), singleLine = false, modifier = Modifier.fillMaxWidth())
                TerminButton(stringResource(R.string.appointment_cancel_confirm), viewModel::cancel, variant = ButtonVariant.Danger, fullWidth = true)
                TerminButton(stringResource(R.string.appointment_keep), viewModel::dismissCancel, variant = ButtonVariant.Text, fullWidth = true)
            }
        }
    }
    state.reschedule?.let { r -> RescheduleSheet(r, viewModel) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RescheduleSheet(r: RescheduleState, viewModel: StaffAppointmentDetailViewModel) {
    val f = formatter
    val spacing = TerminTheme.spacing
    TerminBottomSheet(viewModel::dismissReschedule) {
        Column(
            Modifier
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(stringResource(R.string.staff_reschedule), style = TerminTheme.type.title2, color = TerminTheme.colors.text, modifier = Modifier.semantics { heading() })
            DateField(stringResource(R.string.manual_when), r.date, viewModel::rescheduleDate, Modifier.fillMaxWidth(), min = LocalDate.now(BelgradeZone))
            when {
                r.loading -> SkeletonLines(2)
                r.slots.isNullOrEmpty() -> Notice(stringResource(R.string.manual_no_slots), tone = NoticeTone.Warning)
                else -> FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.slots.forEach { slot ->
                        TerminChip(f.time(slot.start), selected = slot.start == r.selected, onClick = { viewModel.rescheduleSlot(slot.start) })
                    }
                }
            }
            TerminButton(
                stringResource(R.string.action_confirm),
                viewModel::confirmReschedule,
                enabled = r.selected != null,
                loading = r.saving,
                fullWidth = true,
            )
        }
    }
}

private const val HISTORY_SHOWN = 5
