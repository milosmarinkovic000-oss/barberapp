package rs.termin.app.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.InboxMessage
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.InboxRepository
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.formatter
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Kuda vodi dodir na obaveštenje. */
sealed interface InboxTarget {
    data class Appointment(val appointmentId: String, val staff: Boolean) : InboxTarget
    data class Book(val salonId: String, val date: String?, val services: List<String>, val stylistId: String?) : InboxTarget
    data class Salon(val salonId: String) : InboxTarget
    data object Nowhere : InboxTarget
}

fun InboxMessage.target(): InboxTarget {
    val appointment = appointmentId
    val salon = salonId
    return when {
        type == "waitlist_slot" && salon != null -> InboxTarget.Book(salon, dateKey, serviceIds, stylistId)
        appointment != null -> InboxTarget.Appointment(appointment, staff = type.startsWith("staff_"))
        salon != null && type != "stylist_invited" -> InboxTarget.Salon(salon)
        else -> InboxTarget.Nowhere
    }
}

data class InboxUiState(val messages: Loadable<List<InboxMessage>> = Loadable.Loading, val today: LocalDate = LocalDate.MIN) {
    val unreadIds: List<String> get() = messages.valueOrNull().orEmpty().filterNot { it.read }.map { it.id }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val inbox: InboxRepository,
    private val clock: Clock,
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<InboxUiState> = combine(auth.authState, retry) { state, _ -> state }
        .flatMapLatest { state ->
            if (state !is AuthState.SignedIn) {
                flowOf(Loadable.Ready(emptyList()))
            } else {
                inbox.observeInbox(state.uid)
                    .map<List<InboxMessage>, Loadable<List<InboxMessage>>> { Loadable.Ready(it) }
                    .onStart { emit(Loadable.Loading) }
                    .catch { emit(Loadable.Failed(it)) }
            }
        }
        .map { InboxUiState(it, LocalDate.now(clock.withZone(BelgradeZone))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), InboxUiState())

    private fun uid(): String? = (auth.authState.value as? AuthState.SignedIn)?.uid

    fun open(message: InboxMessage) {
        if (message.read) return
        val uid = uid() ?: return
        viewModelScope.launch { runCatching { inbox.markRead(uid, message.id) } }
    }

    fun markAllRead() {
        val uid = uid() ?: return
        val ids = uiState.value.unreadIds
        viewModelScope.launch { runCatching { inbox.markAllRead(uid, ids) } }
    }

    fun delete(id: String) {
        val uid = uid() ?: return
        viewModelScope.launch { runCatching { inbox.delete(uid, id) } }
    }

    fun retry() {
        retry.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun InboxScreen(onBack: () -> Unit, onOpen: (InboxTarget) -> Unit, viewModel: InboxViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = TerminTheme.spacing
    Scaffold(
        containerColor = TerminTheme.colors.background,
        topBar = {
            TerminTopBar(stringResource(R.string.inbox_title), onBack = onBack) {
                if (state.unreadIds.isNotEmpty()) {
                    TerminIconButton(TerminIcon.TaskAlt, stringResource(R.string.inbox_mark_all), viewModel::markAllRead)
                }
            }
        },
    ) { padding ->
        when (val messages = state.messages) {
            Loadable.Loading -> LoadingSkeleton(Modifier.padding(padding).padding(spacing.gutter)) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), TerminTheme.radii.cardShape) }
                }
            }
            is Loadable.Failed -> ErrorState(viewModel::retry, Modifier.padding(padding))
            is Loadable.Ready -> if (messages.value.isEmpty()) {
                EmptyState(Illustration.Inbox, stringResource(R.string.inbox_empty_title), stringResource(R.string.inbox_empty), Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.gutter,
                        end = spacing.gutter,
                        top = padding.calculateTopPadding() + spacing.sm,
                        bottom = padding.calculateBottomPadding() + spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(messages.value, key = { it.id }) { message ->
                        InboxRow(
                            message = message,
                            today = state.today,
                            onClick = {
                                viewModel.open(message)
                                onOpen(message.target())
                            },
                            onDelete = { viewModel.delete(message.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(message: InboxMessage, today: LocalDate, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    val f = formatter
    val unreadLabel = stringResource(R.string.inbox_unread)
    val time = "${f.relativeDay(f.date(message.createdAt), today)} · ${f.time(message.createdAt)}"
    TerminCard(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(unreadLabel.takeIf { !message.read }, message.title, message.body, time).joinToString(". ")
            },
        onClick = onClick,
        color = if (message.read) colors.surface else colors.accentSoft,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp)) {
                TerminIconImage(iconFor(message.type), contentDescription = null, tint = if (message.read) colors.textSecondary else colors.onAccentSoft)
                if (!message.read) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val fg = if (message.read) colors.text else colors.onAccentSoft
                Text(message.title, style = TerminTheme.type.bodyStrong, color = fg)
                Text(message.body, style = TerminTheme.type.callout, color = if (message.read) colors.textSecondary else colors.onAccentSoft)
                Text(time, style = TerminTheme.type.caption, color = if (message.read) colors.textSecondary else colors.onAccentSoft)
            }
            TerminIconButton(TerminIcon.Delete, stringResource(R.string.action_delete), onDelete, tint = colors.textSecondary)
        }
    }
}

private fun iconFor(type: String): TerminIcon = when (type) {
    "booking_confirmed", "staff_new_booking" -> TerminIcon.EventAvailable
    "booking_changed", "staff_rescheduled" -> TerminIcon.Schedule
    "booking_canceled_by_salon", "staff_canceled" -> TerminIcon.EventBusy
    "reminder_24h", "reminder_2h" -> TerminIcon.Alarm
    "review_request", "review_reply" -> TerminIcon.Star
    "waitlist_slot" -> TerminIcon.HourglassTop
    "stylist_invited" -> TerminIcon.Group
    else -> TerminIcon.Notifications
}
