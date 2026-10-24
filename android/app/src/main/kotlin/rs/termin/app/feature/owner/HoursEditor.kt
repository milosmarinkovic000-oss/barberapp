package rs.termin.app.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.domain.model.WeeklyHours
import rs.termin.app.core.ui.TimeField
import java.time.DayOfWeek
import java.time.LocalTime

/** Sve smene su ispravne (kraj posle početka) i ne preklapaju se unutar dana. */
fun WeeklyHours.isValid(): Boolean = days.values.all { ranges ->
    ranges.all { it.end > it.start } && ranges.sortedBy { it.start }.zipWithNext().all { (a, b) -> a.end <= b.start }
}

fun WeeklyHours.withDay(day: DayOfWeek, ranges: List<TimeRange>): WeeklyHours = WeeklyHours(days + (day to ranges))

private val DefaultRange = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))

/**
 * Uređivanje radnog vremena po danima: prekidač „radi / ne radi“ i jedna ili više smena.
 * Isti editor služi i za pauze frizera ([newRange] je tada kraća pauza).
 */
@Composable
fun HoursEditor(hours: WeeklyHours, onChange: (WeeklyHours) -> Unit, modifier: Modifier = Modifier, newRange: TimeRange = DefaultRange) {
    val colors = TerminTheme.colors
    val names = stringArrayResource(R.array.weekday_names)
    TerminCard(modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            if (index > 0) TerminDivider()
            val ranges = hours.on(day)
            val open = ranges.isNotEmpty()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(names[index], style = TerminTheme.type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                    if (!open) Text(stringResource(R.string.info_closed), style = TerminTheme.type.callout, color = colors.textSecondary, modifier = Modifier.padding(end = 8.dp))
                    Switch(
                        checked = open,
                        onCheckedChange = { checked -> onChange(hours.withDay(day, if (checked) listOf(newRange) else emptyList())) },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
                        modifier = Modifier.semantics { contentDescription = names[index] },
                    )
                }
                ranges.forEachIndexed { i, range ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeField(stringResource(R.string.block_from), range.start, { t ->
                            onChange(hours.withDay(day, ranges.toMutableList().also { it[i] = range.copy(start = t) }))
                        }, Modifier.weight(1f))
                        TimeField(stringResource(R.string.block_to), range.end, { t ->
                            onChange(hours.withDay(day, ranges.toMutableList().also { it[i] = range.copy(end = t) }))
                        }, Modifier.weight(1f))
                        if (ranges.size > 1) {
                            TerminIconButton(TerminIcon.Remove, stringResource(R.string.action_delete), {
                                onChange(hours.withDay(day, ranges.filterIndexed { j, _ -> j != i }))
                            })
                        }
                    }
                    if (range.end <= range.start) {
                        Text(stringResource(R.string.info_error_hours), style = TerminTheme.type.caption, color = colors.error)
                    }
                }
                if (open && ranges.size < MAX_RANGES) {
                    val last = ranges.last()
                    TerminButton(
                        stringResource(R.string.info_add_range),
                        {
                            val start = minOf(last.end.plusHours(1), LocalTime.of(22, 0))
                            onChange(hours.withDay(day, ranges + TimeRange(start, minOf(start.plusHours(3), LocalTime.of(23, 0)))))
                        },
                        variant = ButtonVariant.Text,
                        icon = TerminIcon.Add,
                    )
                }
            }
        }
    }
}

private const val MAX_RANGES = 3
