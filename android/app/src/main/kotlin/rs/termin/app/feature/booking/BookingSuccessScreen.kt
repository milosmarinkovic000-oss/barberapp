package rs.termin.app.feature.booking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.formatter
import rs.termin.app.navigation.BookingSuccessRoute
import javax.inject.Inject

data class SuccessUiState(
    val appointment: Appointment? = null,
    val salon: Salon? = null,
    val askNotifications: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookingSuccessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    appointments: AppointmentRepository,
    salons: SalonRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<BookingSuccessRoute>().appointmentId
    private val appointment = appointments.observeAppointment(id).catch { emit(null) }

    val uiState: StateFlow<SuccessUiState> = combine(
        appointment,
        appointment.flatMapLatest { a -> if (a == null) flowOf(null) else salons.observeSalon(a.salonId).catch { emit(null) } },
        preferences.preferences,
    ) { a, salon, prefs ->
        SuccessUiState(a, salon, askNotifications = !prefs.notificationPermissionAsked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SuccessUiState())

    fun notificationsAsked() {
        viewModelScope.launch { preferences.setNotificationPermissionAsked() }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun BookingSuccessScreen(onDone: () -> Unit, viewModel: BookingSuccessViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val f = formatter
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val haptics = LocalHapticFeedback.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.notificationsAsked() }
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(spacing.gutter)
            .readableWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        AnimatedCheck(Modifier.size(128.dp))
        Text(
            stringResource(R.string.success_title),
            style = TerminTheme.type.display,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        val appointment = state.appointment
        if (appointment == null) {
            SkeletonLines(3)
        } else {
            Text(
                stringResource(R.string.success_subtitle, appointment.salonName, f.dateTime(appointment.start)),
                style = TerminTheme.type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            TerminCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(appointment.services.joinToString(", ") { it.name }, style = TerminTheme.type.bodyStrong, color = colors.text)
                    Text(stringResource(R.string.booking_with_stylist, appointment.stylistName), style = TerminTheme.type.callout, color = colors.textSecondary)
                    Text("${f.price(appointment.totalPrice)} · ${f.duration(appointment.durationMin)}", style = TerminTheme.type.price, color = colors.text)
                }
            }
            val calendarTitle = stringResource(R.string.success_calendar_title, appointment.salonName, appointment.services.joinToString(", ") { it.name })
            val shareText = stringResource(R.string.success_share_text, appointment.salonName, f.dateTime(appointment.start))
            val shareTitle = stringResource(R.string.success_share)
            val address = state.salon?.let { "${it.address}, ${it.city}" } ?: appointment.salonAddress
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                TerminButton(
                    stringResource(R.string.success_add_calendar),
                    { Actions.addToCalendar(context, calendarTitle, appointment.start, appointment.end, address, appointment.note) },
                    variant = ButtonVariant.Secondary,
                    icon = TerminIcon.EventAvailable,
                    fullWidth = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    TerminButton(
                        stringResource(R.string.action_directions),
                        { Actions.directions(context, state.salon?.location, address) },
                        variant = ButtonVariant.Secondary,
                        icon = TerminIcon.Directions,
                        modifier = Modifier.weight(1f),
                    )
                    TerminButton(
                        stringResource(R.string.success_share),
                        { Actions.share(context, shareText, shareTitle) },
                        variant = ButtonVariant.Secondary,
                        icon = TerminIcon.Share,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.askNotifications && needsPermission) {
            Notice(stringResource(R.string.notifications_rationale), icon = TerminIcon.Notifications)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                TerminButton(stringResource(R.string.notifications_later), viewModel::notificationsAsked, variant = ButtonVariant.Text)
                TerminButton(
                    stringResource(R.string.notifications_allow),
                    { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    variant = ButtonVariant.Tonal,
                    icon = TerminIcon.Notifications,
                )
            }
        }
        TerminButton(stringResource(R.string.success_done), onDone, fullWidth = true)
    }
}

/** Animacija uspeha: krug i kvačica koja se iscrtava (bez animacije kad je pokret smanjen). */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    val motion = TerminTheme.motion
    val progress = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(motion.enabled) {
        if (motion.enabled) progress.animateTo(1f, tween(durationMillis = motion.durationMs * 2))
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.minDimension
        drawCircle(colors.successSoft, radius = s / 2)
        drawCircle(colors.success, radius = s / 2 * (0.82f + 0.18f * progress.value), style = Stroke(width = s * 0.03f))
        val path = Path().apply {
            moveTo(s * 0.28f, s * 0.52f)
            lineTo(s * 0.44f, s * 0.67f)
            lineTo(s * 0.73f, s * 0.36f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(partial, colors.success, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (progress.value == 0f) drawCircle(colors.success, s * 0.01f, Offset(s * 0.28f, s * 0.52f))
    }
}
