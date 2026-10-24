package rs.termin.app.feature.appointments

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.OfflineBanner
import rs.termin.app.core.designsystem.component.SectionHeader
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.ui.AppointmentCard
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.isWideLayout
import java.time.LocalDate

@Composable
fun AppointmentsScreen(
    contentPadding: PaddingValues,
    onOpenAppointment: (String) -> Unit,
    onFindSalon: () -> Unit,
    onBookFromWaitlist: (salonId: String, date: LocalDate) -> Unit,
    detailPane: @Composable (appointmentId: String) -> Unit,
    viewModel: AppointmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wide = isWideLayout()
    AppointmentsContent(
        state = state,
        wide = wide,
        contentPadding = contentPadding,
        onShowPast = viewModel::showPast,
        onOpen = { id -> if (wide) viewModel.select(id) else onOpenAppointment(id) },
        onFindSalon = onFindSalon,
        onRetry = viewModel::retry,
        onLeaveWaitlist = viewModel::leaveWaitlist,
        onBookFromWaitlist = onBookFromWaitlist,
        detailPane = detailPane,
    )
}

@Composable
fun AppointmentsContent(
    state: AppointmentsUiState,
    wide: Boolean,
    contentPadding: PaddingValues,
    onShowPast: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onFindSalon: () -> Unit,
    onRetry: () -> Unit,
    onLeaveWaitlist: (String) -> Unit,
    onBookFromWaitlist: (String, LocalDate) -> Unit,
    detailPane: @Composable (String) -> Unit,
) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    Row(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(Modifier.weight(if (wide) 0.42f else 1f).fillMaxHeight()) {
            if (!state.online) OfflineBanner()
            Text(
                stringResource(R.string.appointments_title),
                style = TerminTheme.type.title1,
                color = colors.text,
                modifier = Modifier
                    .padding(horizontal = spacing.gutter)
                    .padding(top = spacing.lg, bottom = spacing.sm)
                    .semantics { heading() },
            )
            SegmentedControl(
                options = listOf(false, true),
                selected = state.showPast,
                label = { if (it) stringResource(R.string.appointments_past) else stringResource(R.string.appointments_upcoming) },
                onSelect = onShowPast,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter),
            )
            when (val lists = state.lists) {
                Loadable.Loading -> LoadingSkeleton(Modifier.padding(spacing.gutter)) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(96.dp), TerminTheme.radii.cardShape) }
                    }
                }
                is Loadable.Failed -> ErrorState(onRetry)
                is Loadable.Ready -> AppointmentList(
                    lists = lists.value,
                    state = state,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onOpen = onOpen,
                    onFindSalon = onFindSalon,
                    onLeaveWaitlist = onLeaveWaitlist,
                    onBookFromWaitlist = onBookFromWaitlist,
                )
            }
        }
        if (wide) {
            VerticalDivider(color = colors.divider)
            Box(Modifier.weight(0.58f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val selected = state.selectedId
                if (selected == null) {
                    EmptyState(Illustration.Calendar, stringResource(R.string.appointments_title), stringResource(R.string.appointments_select))
                } else {
                    detailPane(selected)
                }
            }
        }
    }
}

@Composable
private fun AppointmentList(
    lists: AppointmentsLists,
    state: AppointmentsUiState,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onFindSalon: () -> Unit,
    onLeaveWaitlist: (String) -> Unit,
    onBookFromWaitlist: (String, LocalDate) -> Unit,
) {
    val spacing = TerminTheme.spacing
    val items = if (state.showPast) lists.past else lists.upcoming
    LazyColumn(
        contentPadding = PaddingValues(top = spacing.md, bottom = bottomPadding + spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!state.showPast && lists.waitlist.isNotEmpty()) {
            item(key = "waitlist-header", contentType = "header") {
                SectionHeader(stringResource(R.string.appointments_waitlist))
            }
            items(lists.waitlist, key = { "w-${it.id}" }, contentType = { "waitlist" }) { entry ->
                WaitlistCard(
                    entry,
                    onLeave = { onLeaveWaitlist(entry.id) },
                    onBook = { onBookFromWaitlist(entry.salonId, entry.date) },
                    modifier = Modifier.padding(horizontal = spacing.gutter).animateItem(),
                )
            }
            item(key = "appointments-header", contentType = "header") {
                SectionHeader(stringResource(R.string.appointments_upcoming))
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (state.showPast) {
                    EmptyState(Illustration.Calendar, stringResource(R.string.appointments_past), stringResource(R.string.appointments_empty_past))
                } else {
                    EmptyState(
                        Illustration.Calendar,
                        stringResource(R.string.appointments_empty_upcoming_title),
                        stringResource(R.string.appointments_empty_upcoming),
                        actionLabel = stringResource(R.string.appointments_find_salon),
                        onAction = onFindSalon,
                    )
                }
            }
        }
        items(items, key = { it.id }, contentType = { "appointment" }) { appointment ->
            AppointmentCard(
                appointment = appointment,
                now = state.now,
                onClick = { onOpen(appointment.id) },
                selected = appointment.id == state.selectedId,
                modifier = Modifier.padding(horizontal = spacing.gutter).fillMaxWidth().animateItem(),
            )
        }
    }
}

@Composable
private fun WaitlistCard(entry: WaitlistEntry, onLeave: () -> Unit, onBook: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    val f = formatter
    val notified = entry.status == "notified"
    TerminCard(modifier.fillMaxWidth(), color = if (notified) colors.accentSoft else colors.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TerminIconImage(
                    if (notified) TerminIcon.Notifications else TerminIcon.HourglassTop,
                    contentDescription = null,
                    tint = if (notified) colors.onAccentSoft else colors.textSecondary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.appointments_waitlist_entry, entry.salonName, f.dayLong(entry.date)),
                        style = TerminTheme.type.bodyStrong,
                        color = if (notified) colors.onAccentSoft else colors.text,
                    )
                    if (entry.serviceNames.isNotEmpty()) {
                        Text(entry.serviceNames.joinToString(", "), style = TerminTheme.type.callout, color = if (notified) colors.onAccentSoft else colors.textSecondary)
                    }
                    Text(
                        stringResource(if (notified) R.string.appointments_waitlist_notified else R.string.appointments_waitlist_waiting),
                        style = TerminTheme.type.caption,
                        color = if (notified) colors.onAccentSoft else colors.textSecondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminButton(stringResource(R.string.appointments_waitlist_leave), onLeave, variant = ButtonVariant.Text)
                TerminButton(
                    stringResource(R.string.appointments_waitlist_book),
                    onBook,
                    variant = if (notified) ButtonVariant.Primary else ButtonVariant.Tonal,
                    icon = TerminIcon.EventAvailable,
                )
            }
        }
    }
}
