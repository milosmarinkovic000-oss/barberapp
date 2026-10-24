package rs.termin.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import java.time.Instant

/** Boje statusa termina (pozadina i tekst, kontrast ≥ 4.5:1). */
@Composable
fun statusColors(status: AppointmentStatus): Pair<Color, Color> {
    val c = TerminTheme.colors
    return when (status) {
        AppointmentStatus.Confirmed -> c.accentSoft to c.onAccentSoft
        AppointmentStatus.Held -> c.warningSoft to c.onWarningSoft
        AppointmentStatus.Completed -> c.successSoft to c.onSuccessSoft
        AppointmentStatus.NoShow, AppointmentStatus.CanceledByCustomer, AppointmentStatus.CanceledBySalon -> c.errorSoft to c.onErrorSoft
    }
}

@Composable
fun StatusPill(status: AppointmentStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = statusColors(status)
    Text(
        formatter.status(status),
        style = TerminTheme.type.caption,
        color = fg,
        modifier = modifier
            .clip(TerminTheme.radii.smallShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Kartica termina u listi: datum levo (dan i mesec), salon/usluge, vreme i odbrojavanje.
 * [subtitle] zamenjuje naziv salona (npr. ime klijenta u prikazu za osoblje).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentCard(
    appointment: Appointment,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = appointment.salonName,
    showCountdown: Boolean = true,
    selected: Boolean = false,
) {
    val f = formatter
    val colors = TerminTheme.colors
    val date = f.date(appointment.start)
    val upcoming = appointment.status.isActive && appointment.start.isAfter(now)
    val services = appointment.services.joinToString(", ") { it.name }
    val description = listOf(title, services, f.dateTime(appointment.start), f.status(appointment.status)).joinToString(". ")
    TerminCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onClick,
        color = if (selected) colors.accentSoft else colors.surface,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .width(56.dp)
                    .clip(TerminTheme.radii.mediumShape)
                    .background(if (upcoming) colors.accentSoft else colors.surfaceMuted)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(f.weekdayShort(date), style = TerminTheme.type.caption, color = if (upcoming) colors.onAccentSoft else colors.textSecondary)
                Text(date.dayOfMonth.toString(), style = TerminTheme.type.title2, color = if (upcoming) colors.onAccentSoft else colors.text)
                Text(f.monthName(date).take(3), style = TerminTheme.type.caption, color = if (upcoming) colors.onAccentSoft else colors.textSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = TerminTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(services, style = TerminTheme.type.callout, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Uz veliki tekst odbrojavanje prelazi u novi red kao celina.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.timeRange(appointment.start, appointment.end), style = TerminTheme.type.callout, color = colors.text)
                    if (upcoming && showCountdown) {
                        Text("· ${f.countdown(appointment.start, now)}", style = TerminTheme.type.callout, color = colors.accentText)
                    }
                }
                if (!upcoming || appointment.status != AppointmentStatus.Confirmed) {
                    StatusPill(appointment.status, Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
