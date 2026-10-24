package rs.termin.app.feature.booking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.SalonAccentTheme
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.OfflineBanner
import rs.termin.app.core.designsystem.component.RatingSummary
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.StepProgress
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.raised
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.DayPart
import rs.termin.app.core.domain.groupByDayPart
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.ui.Formatter
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.salon.PolicyText
import rs.termin.app.feature.salon.ServiceRow
import rs.termin.app.feature.salon.StylistSheet

@Composable
fun BookingScreen(
    onClose: () -> Unit,
    onCompleted: (appointmentId: String) -> Unit,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completedAppointmentId) {
        state.completedAppointmentId?.let {
            viewModel.completionHandled()
            onCompleted(it)
        }
    }
    BackHandler { if (!viewModel.back()) onClose() }
    val accent = state.data.valueOrNull()?.salon?.accentColor?.let { Color(it) }
    SalonAccentTheme(accent) {
        BookingContent(
            state = state,
            onBack = { if (!viewModel.back()) onClose() },
            onClose = onClose,
            onRetry = viewModel::load,
            onToggleService = viewModel::toggleService,
            onSelectStylist = viewModel::selectStylist,
            onNext = viewModel::next,
            onRetryAvailability = viewModel::loadAvailability,
            onSelectDate = viewModel::selectDate,
            onSelectSlot = viewModel::selectSlot,
            onFirstFree = viewModel::selectFirstFree,
            onJoinWaitlist = viewModel::joinWaitlist,
            onNote = viewModel::onNote,
            onConfirm = viewModel::confirm,
        )
    }
}

@Composable
fun BookingContent(
    state: BookingUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onToggleService: (String) -> Unit,
    onSelectStylist: (String?) -> Unit,
    onNext: () -> Unit,
    onRetryAvailability: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onSelectSlot: (Slot) -> Unit,
    onFirstFree: () -> Unit,
    onJoinWaitlist: () -> Unit,
    onNote: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Header(state, onBack, onClose)
        if (!state.online) OfflineBanner(message = stringResource(R.string.state_offline_booking))
        when (val data = state.data) {
            Loadable.Loading -> Column(Modifier.padding(spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                SkeletonBox(Modifier.fillMaxWidth().height(28.dp))
                SkeletonLines(6)
            }
            is Loadable.Failed -> ErrorState(onRetry)
            is Loadable.Ready -> {
                Box(Modifier.weight(1f)) {
                    val motion = TerminTheme.motion
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = {
                            if (!motion.enabled) {
                                fadeIn(motion.tweenSpec()) togetherWith fadeOut(motion.tweenSpec())
                            } else {
                                val forward = targetState.ordinal > initialState.ordinal
                                (slideInHorizontally(motion.offsetSpec()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.tweenSpec())) togetherWith
                                    (slideOutHorizontally(motion.offsetSpec()) { if (forward) -it / 4 else it / 4 } + fadeOut(motion.tweenSpec()))
                            }
                        },
                        label = "booking-step",
                    ) { step ->
                        when (step) {
                            BookingStep.Services -> ServicesStep(state, data.value, onToggleService, f)
                            BookingStep.Stylist -> StylistStep(state, onSelectStylist, f)
                            BookingStep.Time -> TimeStep(state, onRetryAvailability, onSelectDate, onSelectSlot, onFirstFree, onJoinWaitlist, f)
                            BookingStep.Review -> ReviewStep(state, data.value, onNote, f)
                        }
                    }
                }
                BottomBar(state, onNext, onConfirm, f)
            }
        }
    }
}

@Composable
private fun Header(state: BookingUiState, onBack: () -> Unit, onClose: () -> Unit) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val stepName = stringResource(stepTitle(state.step))
    val label = stringResource(R.string.booking_step, state.stepIndex + 1, state.steps.size, stepName)
    Column(Modifier.padding(bottom = spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminIconButton(TerminIcon.ArrowBack, stringResource(R.string.cd_back), onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (state.isReschedule) R.string.booking_reschedule_title else R.string.booking_title),
                    style = TerminTheme.type.title3,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                state.data.valueOrNull()?.salon?.name?.let { Text(it, style = TerminTheme.type.caption, color = colors.textSecondary) }
            }
            TerminIconButton(TerminIcon.Close, stringResource(R.string.cd_close), onClose)
        }
        Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = TerminTheme.type.caption, color = colors.textSecondary)
            StepProgress(state.stepIndex, state.steps.size, label)
        }
    }
}

private fun stepTitle(step: BookingStep): Int = when (step) {
    BookingStep.Services -> R.string.booking_step_services
    BookingStep.Stylist -> R.string.booking_step_stylist
    BookingStep.Time -> R.string.booking_step_time
    BookingStep.Review -> R.string.booking_step_review
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = TerminTheme.spacing.gutter, vertical = TerminTheme.spacing.sm)) {
        Text(title, style = TerminTheme.type.title1, color = TerminTheme.colors.text, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = TerminTheme.type.body, color = TerminTheme.colors.textSecondary)
    }
}

@Composable
private fun ServicesStep(state: BookingUiState, data: BookingData, onToggle: (String) -> Unit, f: Formatter) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(stringResource(R.string.booking_services_title), stringResource(R.string.booking_services_subtitle))
        data.services.groupBy { it.category }.forEach { (category, services) ->
            Text(
                f.category(category),
                style = TerminTheme.type.title3,
                color = TerminTheme.colors.text,
                modifier = Modifier
                    .padding(start = TerminTheme.spacing.gutter, top = 16.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            services.forEach { service -> ServiceRow(service, service.id in state.selectedServiceIds, { onToggle(service.id) }, f) }
        }
    }
}

@Composable
private fun StylistStep(state: BookingUiState, onSelect: (String?) -> Unit, f: Formatter) {
    val images = LocalImageUrls.current
    var profile by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(TerminTheme.spacing.xs),
    ) {
        StepTitle(stringResource(R.string.booking_stylist_title))
        StylistOption(
            selected = state.stylistId == null,
            title = stringResource(R.string.stylist_any),
            subtitle = stringResource(R.string.stylist_any_description),
            leading = {
                Box(Modifier.size(56.dp).background(TerminTheme.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    TerminIconImage(TerminIcon.Group, contentDescription = null, tint = TerminTheme.colors.onAccentSoft)
                }
            },
            onClick = { onSelect(null) },
        )
        state.capableStylists.forEach { stylist ->
            StylistOption(
                selected = state.stylistId == stylist.id,
                title = stylist.name,
                subtitle = stylist.specialties.joinToString(" · "),
                leading = { Avatar(images.url(stylist.photoPath, ImageSize.Thumb), stylist.name, size = 56.dp) },
                onClick = { onSelect(stylist.id) },
                trailing = { TerminIconButton(TerminIcon.Info, stylist.name, { profile = stylist.id }) },
                rating = stylist,
                f = f,
            )
        }
    }
    profile?.let { id ->
        state.capableStylists.firstOrNull { it.id == id }?.let { stylist ->
            StylistSheet(
                stylist = stylist,
                onBook = {
                    onSelect(stylist.id)
                    profile = null
                },
                onDismiss = { profile = null },
            )
        }
    }
}

@Composable
private fun StylistOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    rating: Stylist? = null,
    f: Formatter? = null,
) {
    val colors = TerminTheme.colors
    Surface(
        color = if (selected) colors.accentSoft else colors.surface,
        shape = TerminTheme.radii.cardShape,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accentText else colors.divider),
        modifier = Modifier
            .padding(horizontal = TerminTheme.spacing.gutter)
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accentText, unselectedColor = colors.outline))
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = TerminTheme.type.bodyStrong, color = colors.text)
                if (subtitle.isNotBlank()) Text(subtitle, style = TerminTheme.type.callout, color = colors.textSecondary)
                if (rating != null && f != null) {
                    RatingSummary(rating.rating.average, rating.rating.count, f.rating(rating.rating.average), f.reviews(rating.rating.count), compact = true)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun TimeStep(
    state: BookingUiState,
    onRetry: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onSelectSlot: (Slot) -> Unit,
    onFirstFree: () -> Unit,
    onJoinWaitlist: () -> Unit,
    f: Formatter,
) {
    val spacing = TerminTheme.spacing
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        StepTitle(stringResource(R.string.booking_time_title))
        state.data.valueOrNull()?.rescheduling?.let {
            Notice(stringResource(R.string.booking_current, f.dateTime(it.start)), Modifier.padding(horizontal = spacing.gutter))
        }
        state.error?.let { ErrorNotice(it) }
        when (val availability = state.availability) {
            null, Loadable.Loading -> Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SkeletonBox(Modifier.fillMaxWidth().height(72.dp))
                SkeletonLines(4)
            }
            is Loadable.Failed -> ErrorState(onRetry)
            is Loadable.Ready -> {
                val days = availability.value.days
                DayStrip(days, state.selectedDate, state.today, onSelectDate, f)
                val anyFree = days.any { it.slots.isNotEmpty() }
                if (anyFree) {
                    TerminButton(
                        stringResource(R.string.booking_first_free),
                        { onFirstFree(); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                        variant = ButtonVariant.Tonal,
                        icon = TerminIcon.Bolt,
                        modifier = Modifier.padding(horizontal = spacing.gutter),
                        enabled = state.online && !state.holding,
                    )
                }
                HoldStatus(state)
                val day = state.selectedDay
                if (day == null || day.slots.isEmpty()) {
                    NoSlots(state, anyFree, onJoinWaitlist)
                } else {
                    SlotGroups(day, state, { slot ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelectSlot(slot)
                    }, f)
                }
            }
        }
    }
}

@Composable
private fun ErrorNotice(error: Throwable) {
    val tone = if (error is BookingError.SlotTaken) NoticeTone.Warning else NoticeTone.Error
    Notice(
        stringResource(error.messageRes()),
        Modifier
            .padding(horizontal = TerminTheme.spacing.gutter)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        tone = tone,
    )
}

@Composable
private fun DayStrip(days: List<DaySlots>, selected: java.time.LocalDate?, today: java.time.LocalDate, onSelect: (java.time.LocalDate) -> Unit, f: Formatter) {
    val colors = TerminTheme.colors
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = TerminTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.selectableGroup(),
    ) {
        items(days, key = { it.date.toString() }, contentType = { "day" }) { day ->
            val isSelected = day.date == selected
            val hasSlots = day.slots.isNotEmpty()
            val label = f.relativeDay(day.date, today)
            val description = stringResource(if (hasSlots) R.string.booking_day_free else R.string.booking_day_full, f.dayLong(day.date))
            Surface(
                shape = TerminTheme.radii.cardShape,
                color = if (isSelected) colors.accent else colors.surface,
                contentColor = if (isSelected) colors.onAccent else colors.text,
                border = if (isSelected) null else BorderStroke(1.dp, colors.divider),
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .defaultMinSize(minHeight = 84.dp)
                    .selectable(isSelected, onClick = { onSelect(day.date) }, role = Role.Tab)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (day.date == today) label else f.weekdayShort(day.date), style = TerminTheme.type.caption, textAlign = TextAlign.Center, maxLines = 1)
                    Text(day.date.dayOfMonth.toString(), style = TerminTheme.type.title3)
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    !hasSlots -> Color.Transparent
                                    isSelected -> colors.onAccent
                                    else -> colors.accentText
                                },
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldStatus(state: BookingUiState) {
    val spacing = TerminTheme.spacing
    when {
        state.holding -> Notice(stringResource(R.string.booking_holding), Modifier.padding(horizontal = spacing.gutter), icon = TerminIcon.HourglassTop)
        state.hold != null -> {
            val left = state.holdSecondsLeft
            Notice(
                stringResource(R.string.booking_hold, "%d:%02d".format(left / 60, left % 60)),
                Modifier.padding(horizontal = spacing.gutter),
                tone = NoticeTone.Success,
                icon = TerminIcon.Timer,
            )
        }
        state.holdExpired -> Notice(stringResource(R.string.booking_hold_expired), Modifier.padding(horizontal = spacing.gutter), tone = NoticeTone.Warning)
    }
}

@Composable
private fun NoSlots(state: BookingUiState, anyFree: Boolean, onJoinWaitlist: () -> Unit) {
    val spacing = TerminTheme.spacing
    Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Notice(stringResource(if (anyFree) R.string.booking_no_slots_day else R.string.booking_no_slots_any))
        if (TerminTheme.features.waitlist && !state.isReschedule && state.selectedDate != null) {
            if (state.waitlistJoined) {
                Notice(stringResource(R.string.booking_waitlist_joined), tone = NoticeTone.Success, icon = TerminIcon.Notifications)
            } else {
                TerminButton(
                    stringResource(R.string.booking_waitlist),
                    onJoinWaitlist,
                    variant = ButtonVariant.Secondary,
                    icon = TerminIcon.Notifications,
                    loading = state.joiningWaitlist,
                    enabled = state.online,
                )
            }
        }
    }
}

@Composable
private fun SlotGroups(day: DaySlots, state: BookingUiState, onSelect: (Slot) -> Unit, f: Formatter) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val groups = remember(day) { groupByDayPart(day.slots) }
    Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        groups.forEach { (part, slots) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        when (part) {
                            DayPart.Morning -> R.string.booking_morning
                            DayPart.Afternoon -> R.string.booking_afternoon
                            DayPart.Evening -> R.string.booking_evening
                        },
                    ),
                    style = TerminTheme.type.title3,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    slots.forEach { slot ->
                        val selected = state.selectedSlot?.start == slot.start
                        val description = stringResource(R.string.booking_slot, f.dayLong(day.date), f.time(slot.start))
                        Surface(
                            shape = TerminTheme.radii.buttonShape,
                            color = if (selected) colors.accent else colors.surface,
                            contentColor = if (selected) colors.onAccent else colors.text,
                            border = if (selected) null else BorderStroke(1.dp, colors.outline),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 76.dp, minHeight = 48.dp)
                                .selectable(selected, enabled = state.online && !state.holding, role = Role.RadioButton, onClick = { onSelect(slot) })
                                .semantics { contentDescription = description },
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(f.time(slot.start), style = TerminTheme.type.bodyStrong)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(state: BookingUiState, data: BookingData, onNote: (String) -> Unit, f: Formatter) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val start = state.hold?.start ?: state.selectedSlot?.start
    val stylistName = state.hold?.stylistName
        ?: state.stylistId?.let { id -> data.stylists.firstOrNull { it.id == id }?.name }
        ?: data.rescheduling?.stylistName
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        StepTitle(stringResource(R.string.booking_review_title))
        state.error?.let { ErrorNotice(it) }
        HoldStatus(state)
        TerminCard(Modifier.padding(horizontal = spacing.gutter)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryRow(stringResource(R.string.booking_summary_salon), "${data.salon.name}\n${data.salon.address}")
                if (start != null) SummaryRow(stringResource(R.string.booking_summary_when), f.dateTime(start))
                SummaryRow(
                    stringResource(R.string.booking_summary_stylist),
                    stylistName ?: stringResource(R.string.stylist_any),
                )
                if (state.stylistId == null && state.hold == null && !state.isReschedule) {
                    Text(stringResource(R.string.booking_any_assigned), style = TerminTheme.type.caption, color = colors.textSecondary)
                }
                TerminDivider()
                Text(stringResource(R.string.booking_summary_services), style = TerminTheme.type.caption, color = colors.textSecondary)
                val lines = data.rescheduling?.services?.map { it.name to it.price } ?: state.services.map { it.name to it.price }
                lines.forEach { (name, price) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(name, style = TerminTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
                        Text(f.price(price), style = TerminTheme.type.price, color = colors.text)
                    }
                }
                TerminDivider()
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.booking_total_label), style = TerminTheme.type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                    Text("${f.price(state.totalPrice)} · ${f.duration(state.totalDuration)}", style = TerminTheme.type.price, color = colors.text)
                }
            }
        }
        if (!state.isReschedule) {
            TerminTextField(
                value = state.note,
                onValueChange = onNote,
                label = stringResource(R.string.booking_note),
                placeholder = stringResource(R.string.booking_note_placeholder),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.padding(horizontal = spacing.gutter),
            )
        }
        Column(Modifier.padding(horizontal = spacing.gutter)) { PolicyText(data.salon, f) }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = TerminTheme.type.caption, color = TerminTheme.colors.textSecondary)
        Text(value, style = TerminTheme.type.bodyStrong, color = TerminTheme.colors.text)
    }
}

/** Donja traka: uvek prikazuje ukupnu cenu i trajanje, a glavna akcija je u zoni palca. */
@Composable
private fun BottomBar(state: BookingUiState, onNext: () -> Unit, onConfirm: () -> Unit, f: Formatter) {
    val colors = TerminTheme.colors
    val haptics = LocalHapticFeedback.current
    val isLast = state.step == BookingStep.Review
    // Uz veliki tekst zbir i dugme ne staju u jedan red: tada idu jedno ispod drugog.
    val stacked = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val totals: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.booking_total_label), style = TerminTheme.type.caption, color = colors.textSecondary)
            Text(
                if (state.totalDuration == 0) stringResource(R.string.booking_no_selection)
                else stringResource(R.string.booking_total, f.price(state.totalPrice), f.duration(state.totalDuration)),
                style = TerminTheme.type.price,
                color = colors.text,
            )
        }
    }
    val action: @Composable () -> Unit = {
        if (isLast) {
            TerminButton(
                stringResource(if (state.isReschedule) R.string.booking_confirm_reschedule else R.string.booking_confirm),
                {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onConfirm()
                },
                loading = state.confirming,
                enabled = state.online && (state.hold != null || (state.isReschedule && state.selectedSlot != null)),
                icon = TerminIcon.Check,
                fullWidth = stacked,
            )
        } else {
            TerminButton(
                stringResource(if (state.step == BookingStep.Time && !state.canContinue) R.string.booking_select_time_first else R.string.booking_next),
                onNext,
                enabled = state.canContinue && state.online,
                icon = TerminIcon.ArrowForward,
                fullWidth = stacked,
            )
        }
    }
    Surface(
        color = colors.surface,
        shape = TerminTheme.radii.sheetShape,
        modifier = Modifier
            .fillMaxWidth()
            .raised(TerminTheme.surfaces.raisedElevation, TerminTheme.radii.sheetShape),
    ) {
        val padding = Modifier
            .navigationBarsPadding()
            .padding(horizontal = TerminTheme.spacing.gutter, vertical = 12.dp)
        if (stacked) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                totals(Modifier.fillMaxWidth())
                action()
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                totals(Modifier.weight(1f))
                action()
            }
        }
    }
}

private const val LARGE_FONT_SCALE = 1.3f
