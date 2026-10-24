package rs.termin.app.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.ui.DateField
import rs.termin.app.core.ui.Stepper
import rs.termin.app.core.ui.TimeField
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = TerminTheme.type.title2,
        color = TerminTheme.colors.text,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Ručni unos termina (telefonom ili klijent bez najave). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualBookingSheet(
    session: StaffSession,
    stylistId: String?,
    date: LocalDate,
    start: Instant?,
    key: String,
    onDismiss: () -> Unit,
    onSaved: (appointmentId: String) -> Unit,
) {
    val viewModel = hiltViewModel<ManualBookingViewModel>(key = key)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = formatter
    LaunchedEffect(key) { viewModel.prepare(session, stylistId, date, start) }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    TerminBottomSheet(onDismiss) {
        Column(
            Modifier
                .heightIn(max = 720.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TerminTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TerminTheme.spacing.md),
        ) {
            SheetTitle(stringResource(R.string.manual_title))
            state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
            if (state.stylists.isEmpty() && state.services.isEmpty() && state.error == null) {
                SkeletonLines(4)
                return@Column
            }
            if (state.stylists.size > 1) {
                Overline(stringResource(R.string.manual_stylist))
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.stylists.forEach { s ->
                        TerminChip(s.name, selected = s.id == state.stylistId, onClick = { viewModel.selectStylist(s.id) })
                    }
                }
            }
            Overline(stringResource(R.string.manual_services))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availableServices.forEach { service ->
                    TerminChip(
                        "${service.name} · ${f.duration(service.durationMin)}",
                        selected = service.id in state.serviceIds,
                        onClick = { viewModel.toggleService(service.id) },
                    )
                }
            }
            if (state.showErrors && state.serviceIds.isEmpty()) {
                Text(stringResource(R.string.manual_error_services), style = TerminTheme.type.caption, color = TerminTheme.colors.error)
            }
            if (state.serviceIds.isNotEmpty()) {
                val totals = state.totals()
                Text("${f.duration(totals.durationMin)} · ${f.price(totals.price)}", style = TerminTheme.type.price, color = TerminTheme.colors.text)
            }

            Overline(stringResource(R.string.manual_when))
            TerminSwitchRow(
                title = stringResource(R.string.manual_walk_in),
                subtitle = stringResource(R.string.manual_walk_in_text),
                checked = state.walkIn,
                onCheckedChange = viewModel::setWalkIn,
            )
            if (!state.walkIn) {
                DateField(stringResource(R.string.manual_when), state.date, viewModel::selectDate, Modifier.fillMaxWidth())
                when {
                    state.serviceIds.isEmpty() || state.stylistId == null -> Unit
                    state.loadingSlots -> SkeletonLines(2)
                    state.slots.isNullOrEmpty() -> Notice(stringResource(R.string.manual_no_slots), tone = NoticeTone.Warning)
                    else -> {
                        Text(stringResource(R.string.manual_pick_slot), style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
                        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.slots.orEmpty().forEach { slot ->
                                TerminChip(f.time(slot.start), selected = slot.start == state.start, onClick = { viewModel.selectSlot(slot.start) })
                            }
                        }
                    }
                }
            }

            Overline(stringResource(R.string.manual_customer))
            TerminTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = stringResource(R.string.manual_customer_name),
                leadingIcon = TerminIcon.Person,
                error = if (state.showErrors && state.name.trim().length < 2) stringResource(R.string.manual_error_name) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TerminTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = stringResource(R.string.manual_customer_phone),
                leadingIcon = TerminIcon.Call,
                supporting = stringResource(R.string.manual_phone_hint),
                error = if (!state.phoneValid) stringResource(R.string.auth_error_invalid_phone) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TerminTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = stringResource(R.string.manual_note),
                leadingIcon = TerminIcon.Notes,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            TerminButton(stringResource(R.string.manual_save), viewModel::save, icon = TerminIcon.EventAvailable, loading = state.saving, fullWidth = true)
        }
    }
}

@Composable
fun timeOffKindLabel(kind: TimeOffKind): String = stringResource(
    when (kind) {
        TimeOffKind.Block -> R.string.block_kind_block
        TimeOffKind.Vacation -> R.string.block_kind_vacation
        TimeOffKind.Sick -> R.string.block_kind_sick
        TimeOffKind.Other -> R.string.block_kind_other
    },
)

/** Blokiranje vremena ili odsustvo frizera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockTimeSheet(
    session: StaffSession,
    stylistId: String?,
    date: LocalDate,
    from: LocalTime?,
    key: String,
    onDismiss: () -> Unit,
) {
    val viewModel = hiltViewModel<BlockTimeViewModel>(key = key)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(key) { viewModel.prepare(session, stylistId, date, from) }
    TerminBottomSheet(onDismiss) {
        Column(
            Modifier
                .heightIn(max = 720.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TerminTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TerminTheme.spacing.md),
        ) {
            SheetTitle(stringResource(R.string.block_title))
            state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
            val result = state.result
            if (result != null) {
                if (result.conflicts > 0) {
                    Notice(pluralStringResource(R.plurals.block_conflicts, result.conflicts, result.conflicts), tone = NoticeTone.Warning)
                } else {
                    Notice(stringResource(R.string.block_saved), tone = NoticeTone.Success)
                }
                TerminButton(stringResource(R.string.action_done), onDismiss, fullWidth = true)
                return@Column
            }
            if (state.stylists.size > 1) {
                Overline(stringResource(R.string.manual_stylist))
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.stylists.forEach { s ->
                        TerminChip(s.name, selected = s.id == state.stylistId, onClick = { viewModel.selectStylist(s.id) })
                    }
                }
            }
            Overline(stringResource(R.string.block_kind))
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeOffKind.entries.forEach { kind ->
                    TerminChip(timeOffKindLabel(kind), selected = kind == state.kind, onClick = { viewModel.selectKind(kind) })
                }
            }
            DateField(stringResource(R.string.block_from), state.date, viewModel::setDate, Modifier.fillMaxWidth(), min = state.today)
            TerminSwitchRow(stringResource(R.string.block_all_day), state.allDay, viewModel::setAllDay)
            if (state.allDay) {
                Stepper(
                    label = stringResource(R.string.block_days),
                    value = state.days,
                    onChange = viewModel::setDays,
                    range = 1..60,
                    step = 1,
                    display = { it.toString() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(stringResource(R.string.block_from), state.from, viewModel::setFrom, Modifier.weight(1f))
                    TimeField(stringResource(R.string.block_to), state.to, viewModel::setTo, Modifier.weight(1f))
                }
                if (!state.rangeValid) Text(stringResource(R.string.block_error_range), style = TerminTheme.type.caption, color = TerminTheme.colors.error)
            }
            TerminTextField(
                value = state.reason,
                onValueChange = viewModel::onReason,
                label = stringResource(R.string.block_reason),
                leadingIcon = TerminIcon.Notes,
                modifier = Modifier.fillMaxWidth(),
            )
            TerminButton(
                stringResource(R.string.block_save),
                viewModel::save,
                icon = TerminIcon.Block,
                enabled = state.valid,
                loading = state.saving,
                fullWidth = true,
            )
        }
    }
}
