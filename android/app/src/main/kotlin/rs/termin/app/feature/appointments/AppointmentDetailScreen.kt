package rs.termin.app.feature.appointments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.RatingInput
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDialog
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.isUpcoming
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.StatusPill
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.salon.toRouteArg

/**
 * Detalj termina za klijenta. [onBack] je null kada je ekran desni panel na širokom ekranu.
 * [onReschedule] i [onBookAgain] otvaraju tok zakazivanja sa unapred izabranim uslugama i frizerom.
 */
@Composable
fun AppointmentDetailScreen(
    appointmentId: String,
    onBack: (() -> Unit)?,
    onReschedule: (salonId: String, services: String?, stylist: String?, rescheduleId: String) -> Unit,
    onBookAgain: (salonId: String, services: String?, stylist: String?) -> Unit,
    onOpenSalon: (String) -> Unit,
) {
    val viewModel = hiltViewModel<AppointmentDetailViewModel, AppointmentDetailViewModel.Factory>(key = appointmentId) { it.create(appointmentId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        containerColor = TerminTheme.colors.background,
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
            when (val detail = state.detail) {
                Loadable.Loading -> LoadingSkeleton(Modifier.padding(TerminTheme.spacing.gutter)) { SkeletonLines(6) }
                is Loadable.Failed -> ErrorState(onRetry = viewModel::retry)
                is Loadable.Ready -> {
                    val value = detail.value
                    if (value == null) {
                        EmptyState(Illustration.Calendar, stringResource(R.string.appointment_not_found), stringResource(R.string.appointments_select))
                    } else {
                        DetailBody(
                            state = state,
                            appointment = value.appointment,
                            onReschedule = {
                                val a = value.appointment
                                onReschedule(a.salonId, a.services.map { it.id }.toRouteArg(), a.stylistId.takeUnless { a.anyStylist }, a.id)
                            },
                            onCancel = viewModel::requestCancel,
                            onBookAgain = {
                                val a = value.appointment
                                onBookAgain(a.salonId, a.services.map { it.id }.toRouteArg(), a.stylistId.takeUnless { a.anyStylist })
                            },
                            onOpenSalon = { onOpenSalon(value.appointment.salonId) },
                            onDirections = { Actions.directions(context, value.salon?.location, value.appointment.salonAddress) },
                            onCall = { Actions.call(context, value.appointment.salonPhone) },
                            onRating = viewModel::setRating,
                            onReviewText = viewModel::setReviewText,
                            onSubmitReview = viewModel::submitReview,
                        )
                    }
                }
            }
        }
    }
    val appointment = state.detail.valueOrNull()?.appointment
    if (state.confirmCancel && appointment != null) {
        TerminDialog(
            title = stringResource(R.string.appointment_cancel_title),
            text = stringResource(R.string.appointment_cancel_text, formatter.dateTime(appointment.start)),
            confirmLabel = stringResource(R.string.appointment_cancel_confirm),
            onConfirm = viewModel::cancel,
            onDismiss = viewModel::dismissDialogs,
            dismissLabel = stringResource(R.string.appointment_keep),
            destructive = true,
        )
    }
    val phone = state.callSalonPhone
    if (phone != null) {
        val hours = state.detail.valueOrNull()?.salon?.rules?.cancelHours ?: 0
        TerminDialog(
            title = stringResource(R.string.appointment_deadline_title),
            text = stringResource(R.string.appointment_deadline_text, hours),
            confirmLabel = stringResource(R.string.appointment_call_salon),
            onConfirm = {
                viewModel.dismissDialogs()
                Actions.call(context, phone)
            },
            onDismiss = viewModel::dismissDialogs,
            dismissLabel = stringResource(R.string.action_close),
        )
    }
}

@Composable
private fun DetailBody(
    state: AppointmentDetailUiState,
    appointment: Appointment,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
    onBookAgain: () -> Unit,
    onOpenSalon: () -> Unit,
    onDirections: () -> Unit,
    onCall: () -> Unit,
    onRating: (Int) -> Unit,
    onReviewText: (String) -> Unit,
    onSubmitReview: () -> Unit,
) {
    val f = formatter
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val context = LocalContext.current
    val upcoming = isUpcoming(appointment, state.now)
    Column(
        Modifier
            .readableWidth()
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StatusPill(appointment.status)
            Text(
                appointment.salonName,
                style = TerminTheme.type.title1,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            if (upcoming) {
                Text(f.countdown(appointment.start, state.now), style = TerminTheme.type.bodyStrong, color = colors.accentText)
            }
        }
        if (!state.online) Notice(stringResource(R.string.state_offline), tone = NoticeTone.Warning, icon = TerminIcon.WifiOff)
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        if (state.canceled) Notice(stringResource(R.string.appointment_canceled), tone = NoticeTone.Success)

        TerminCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(TerminIcon.Event, stringResource(R.string.appointment_when), f.dateTime(appointment.start), f.timeRange(appointment.start, appointment.end))
                TerminDivider()
                InfoRow(TerminIcon.LocationOn, stringResource(R.string.appointment_where), appointment.salonAddress, null, onClick = onOpenSalon)
                TerminDivider()
                InfoRow(TerminIcon.ContentCut, stringResource(R.string.appointment_stylist), appointment.stylistName, null)
            }
        }

        TerminCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Overline(stringResource(R.string.appointment_services))
                appointment.services.forEach { service ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(service.name, style = TerminTheme.type.body, color = colors.text)
                            Text(f.duration(service.durationMin), style = TerminTheme.type.caption, color = colors.textSecondary)
                        }
                        Text(f.price(service.price), style = TerminTheme.type.price, color = colors.text)
                    }
                }
                TerminDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.appointment_total), style = TerminTheme.type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                    Text(f.price(appointment.totalPrice), style = TerminTheme.type.price, color = colors.text)
                }
                if (appointment.note.isNotBlank()) {
                    TerminDivider()
                    Overline(stringResource(R.string.appointment_note))
                    Text(appointment.note, style = TerminTheme.type.body, color = colors.text)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            TerminButton(stringResource(R.string.action_directions), onDirections, variant = ButtonVariant.Secondary, icon = TerminIcon.Directions, modifier = Modifier.weight(1f))
            TerminButton(stringResource(R.string.action_call), onCall, variant = ButtonVariant.Secondary, icon = TerminIcon.Call, modifier = Modifier.weight(1f))
        }

        if (upcoming) {
            val deadline = state.deadline
            if (deadline != null) {
                if (state.canChange) {
                    Notice(stringResource(R.string.appointment_deadline_until, f.dateTime(deadline)), icon = TerminIcon.Schedule)
                } else {
                    val hours = state.detail.valueOrNull()?.salon?.rules?.cancelHours ?: 0
                    Notice(stringResource(R.string.appointment_deadline_text, hours), tone = NoticeTone.Warning)
                }
            }
            val calendarTitle = stringResource(R.string.success_calendar_title, appointment.salonName, appointment.services.joinToString(", ") { it.name })
            TerminButton(
                stringResource(R.string.success_add_calendar),
                { Actions.addToCalendar(context, calendarTitle, appointment.start, appointment.end, appointment.salonAddress, appointment.note) },
                variant = ButtonVariant.Tonal,
                icon = TerminIcon.EventAvailable,
                fullWidth = true,
            )
            TerminButton(
                stringResource(R.string.appointment_reschedule),
                onReschedule,
                variant = ButtonVariant.Secondary,
                icon = TerminIcon.Schedule,
                enabled = state.canChange && state.online,
                fullWidth = true,
            )
            TerminButton(
                stringResource(R.string.appointment_cancel),
                onCancel,
                variant = ButtonVariant.Danger,
                icon = TerminIcon.EventBusy,
                loading = state.canceling,
                enabled = state.online,
                fullWidth = true,
            )
        } else {
            TerminButton(stringResource(R.string.appointments_book_again), onBookAgain, icon = TerminIcon.Refresh, fullWidth = true)
            when {
                appointment.reviewed || state.reviewSent -> Notice(
                    stringResource(if (state.reviewSent) R.string.appointment_review_thanks else R.string.appointment_reviewed),
                    tone = NoticeTone.Success,
                    icon = TerminIcon.StarFilled,
                )
                state.canReview -> ReviewForm(state, onRating, onReviewText, onSubmitReview)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: TerminIcon, label: String, value: String, secondary: String?, onClick: (() -> Unit)? = null) {
    val colors = TerminTheme.colors
    val clickable = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Row(clickable.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TerminIconImage(icon, contentDescription = null, tint = colors.accentText)
        Column(Modifier.weight(1f)) {
            Text(label, style = TerminTheme.type.caption, color = colors.textSecondary)
            Text(value, style = TerminTheme.type.bodyStrong, color = colors.text)
            if (secondary != null) Text(secondary, style = TerminTheme.type.callout, color = colors.textSecondary)
        }
        if (onClick != null) TerminIconImage(TerminIcon.ChevronRight, contentDescription = null, tint = colors.textSecondary)
    }
}

@Composable
private fun ReviewForm(
    state: AppointmentDetailUiState,
    onRating: (Int) -> Unit,
    onReviewText: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = TerminTheme.colors
    TerminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.appointment_review_title), style = TerminTheme.type.title3, color = colors.text, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.appointment_review_text), style = TerminTheme.type.callout, color = colors.textSecondary)
            RatingInput(state.reviewRating, onRating)
            TerminTextField(
                value = state.reviewText,
                onValueChange = onReviewText,
                label = stringResource(R.string.appointment_review_hint),
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            TerminButton(
                stringResource(R.string.appointment_review_submit),
                onSubmit,
                enabled = state.reviewRating in 1..5 && state.online,
                loading = state.submittingReview,
                fullWidth = true,
            )
            if (state.reviewRating == 0) {
                Text(stringResource(R.string.appointment_review_pick), style = TerminTheme.type.caption, color = colors.textSecondary)
            }
        }
    }
}
