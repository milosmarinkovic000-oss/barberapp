package rs.termin.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Polje koje prikazuje vrednost i otvara izbor na dodir (vreme ili datum). */
@Composable
private fun PickerField(label: String, value: String, icon: TerminIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    Surface(
        onClick = onClick,
        shape = TerminTheme.radii.mediumShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .semantics {
                contentDescription = "$label: $value"
                role = Role.Button
            },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TerminIconImage(icon, contentDescription = null, tint = colors.textSecondary)
            Column {
                Text(label, style = TerminTheme.type.caption, color = colors.textSecondary)
                Text(value, style = TerminTheme.type.bodyStrong, color = colors.text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, formatter.time(value), TerminIcon.Schedule, { open = true }, modifier)
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        val colors = TerminTheme.colors
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.time_picker_title), style = TerminTheme.type.title3) },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = colors.accent,
                        timeSelectorSelectedContainerColor = colors.accentSoft,
                        timeSelectorSelectedContentColor = colors.onAccentSoft,
                    ),
                )
            },
            confirmButton = {
                TerminButton(stringResource(R.string.action_confirm), {
                    open = false
                    onChange(LocalTime.of(state.hour, state.minute))
                })
            },
            dismissButton = { TerminButton(stringResource(R.string.action_cancel), { open = false }, variant = ButtonVariant.Text) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, min: LocalDate? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, formatter.dayLong(value), TerminIcon.Today, { open = true }, modifier)
    if (open) {
        val minMillis = min?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = minMillis == null || utcTimeMillis >= minMillis
            },
        )
        val colors = TerminTheme.colors
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TerminButton(stringResource(R.string.action_confirm), {
                    open = false
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                })
            },
            dismissButton = { TerminButton(stringResource(R.string.action_cancel), { open = false }, variant = ButtonVariant.Text) },
            colors = DatePickerDefaults.colors(containerColor = colors.surface),
        ) {
            DatePicker(
                state = state,
                title = {
                    Text(
                        stringResource(R.string.date_picker_title),
                        style = TerminTheme.type.callout,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = colors.surface,
                    selectedDayContainerColor = colors.accent,
                    selectedDayContentColor = colors.onAccent,
                    todayDateBorderColor = colors.accentText,
                    todayContentColor = colors.accentText,
                ),
            )
        }
    }
}

/** Broj sa dugmadima − / + (trajanje, pravila salona). */
@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int, display: (Int) -> String, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TerminTheme.type.callout, color = colors.textSecondary)
            Text(display(value), style = TerminTheme.type.bodyStrong, color = colors.text)
        }
        TerminIconButton(
            TerminIcon.Remove,
            stringResource(R.string.cd_decrease) + " " + label,
            { onChange((value - step).coerceIn(range)) },
            enabled = value - step >= range.first,
        )
        TerminIconButton(
            TerminIcon.Add,
            stringResource(R.string.cd_increase) + " " + label,
            { onChange((value + step).coerceIn(range)) },
            enabled = value + step <= range.last,
        )
    }
}
