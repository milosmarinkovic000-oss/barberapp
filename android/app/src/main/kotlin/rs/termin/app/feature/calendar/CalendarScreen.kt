package rs.termin.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ChipRow
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.OfflineBanner
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminExtendedFab
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.StatusPill
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.statusColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Otvoren list: novi termin ili blokada, sa unapred izabranim frizerom i vremenom. */
private data class SheetRequest(val block: Boolean, val stylistId: String?, val date: LocalDate, val startMillis: Long?, val key: String)

private val SheetRequestSaver = Saver<SheetRequest?, String>(
    save = { r -> r?.let { "${it.block}|${it.stylistId.orEmpty()}|${it.date}|${it.startMillis ?: ""}|${it.key}" } ?: "" },
    restore = { s ->
        s.split('|').takeIf { it.size == 5 }?.let { p ->
            SheetRequest(p[0].toBoolean(), p[1].ifEmpty { null }, LocalDate.parse(p[2]), p[3].toLongOrNull(), p[4])
        }
    },
)

private val BaseHourHeight = 72.dp
private val BaseTimeColumnWidth = 52.dp
private const val MAX_GRID_SCALE = 1.6f
private const val MAX_TIME_SCALE = 2f
private const val ONE_LINE_MINUTES = 45
private val MinColumnWidth = 148.dp
private const val SNAP_MINUTES = 15

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues,
    onOpenAppointment: (String) -> Unit,
    onOpenTeam: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    var sheet by rememberSaveable(stateSaver = SheetRequestSaver) { mutableStateOf(null) }
    var counter by rememberSaveable { mutableStateOf(0) }
    val session = state.session
    val today = state.now.atZone(BelgradeZone).toLocalDate()

    fun open(block: Boolean, stylistId: String?, date: LocalDate, start: Instant?) {
        counter += 1
        sheet = SheetRequest(block, stylistId, date, start?.toEpochMilli(), "sheet-$counter")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!state.online) OfflineBanner()
            Header(
                state = state,
                today = today,
                onMode = viewModel::setMode,
                onPrev = viewModel::previous,
                onNext = viewModel::next,
                onToday = viewModel::today,
            )
            val data = state.data
            if (state.mode == CalendarMode.Week && session?.isOwner == true && data is Loadable.Ready && data.value.stylists.size > 1) {
                ChipRow(Modifier.padding(bottom = spacing.xs)) {
                    TerminChip(stringResource(R.string.calendar_all_stylists), state.stylistFilter == null, { viewModel.filterStylist(null) })
                    data.value.stylists.forEach { s -> TerminChip(s.name, state.stylistFilter == s.id, { viewModel.filterStylist(s.id) }) }
                }
            }
            when (data) {
                Loadable.Loading -> LoadingSkeleton(Modifier.padding(spacing.gutter)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(480.dp), TerminTheme.radii.cardShape)
                }
                is Loadable.Failed -> ErrorState(viewModel::retry)
                is Loadable.Ready -> {
                    val d = data.value
                    if (d.stylists.isEmpty()) {
                        EmptyState(
                            Illustration.Team,
                            stringResource(R.string.calendar_title),
                            stringResource(R.string.calendar_no_stylists),
                            actionLabel = stringResource(R.string.calendar_add_team).takeIf { session?.isOwner == true },
                            onAction = onOpenTeam.takeIf { session?.isOwner == true },
                        )
                    } else if (state.mode == CalendarMode.Day) {
                        DayGrid(
                            data = d,
                            date = state.date,
                            now = state.now,
                            bottomPadding = contentPadding.calculateBottomPadding() + 88.dp,
                            onOpen = onOpenAppointment,
                            onEmptyTap = { stylistId, start -> open(false, stylistId, state.date, start) },
                        )
                    } else {
                        WeekAgenda(
                            data = d,
                            days = state.days,
                            today = today,
                            now = state.now,
                            showStylist = session?.isOwner == true && state.stylistFilter == null,
                            bottomPadding = contentPadding.calculateBottomPadding() + 88.dp,
                            onOpen = onOpenAppointment,
                            onDay = viewModel::selectDate,
                        )
                    }
                }
            }
        }
        if (session != null) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = spacing.gutter, bottom = contentPadding.calculateBottomPadding() + spacing.md),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallFloatingActionButton(
                    onClick = { open(true, session.stylistFilter, state.date, null) },
                    containerColor = colors.surfaceRaised,
                    contentColor = colors.text,
                ) {
                    TerminIconImage(TerminIcon.Block, contentDescription = stringResource(R.string.calendar_block))
                }
                TerminExtendedFab(stringResource(R.string.calendar_new), TerminIcon.Add, { open(false, session.stylistFilter, state.date, null) })
            }
        }
    }

    val request = sheet
    if (request != null && session != null) {
        val start = request.startMillis?.let(Instant::ofEpochMilli)
        if (request.block) {
            BlockTimeSheet(
                session = session,
                stylistId = request.stylistId,
                date = request.date,
                from = start?.atZone(BelgradeZone)?.toLocalTime(),
                key = request.key,
                onDismiss = { sheet = null },
            )
        } else {
            ManualBookingSheet(
                session = session,
                stylistId = request.stylistId,
                date = request.date,
                start = start,
                key = request.key,
                onDismiss = { sheet = null },
                onSaved = { sheet = null },
            )
        }
    }
}

@Composable
private fun Header(
    state: CalendarUiState,
    today: LocalDate,
    onMode: (CalendarMode) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    Column(Modifier.padding(horizontal = spacing.gutter, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.calendar_title), style = TerminTheme.type.title1, color = colors.text, modifier = Modifier.semantics { heading() })
                val label = if (state.mode == CalendarMode.Day) {
                    f.relativeDay(state.date, today)
                } else {
                    stringResource(R.string.stats_week_of, f.dayMonth(state.weekStart))
                }
                Text(label, style = TerminTheme.type.callout, color = colors.textSecondary)
            }
            TerminIconButton(TerminIcon.ChevronLeft, stringResource(R.string.calendar_prev), onPrev)
            TerminIconButton(TerminIcon.Today, stringResource(R.string.calendar_today), onToday, selected = state.date == today)
            TerminIconButton(TerminIcon.ChevronRight, stringResource(R.string.calendar_next), onNext)
        }
        SegmentedControl(
            options = CalendarMode.entries,
            selected = state.mode,
            label = { if (it == CalendarMode.Day) stringResource(R.string.calendar_day) else stringResource(R.string.calendar_week) },
            onSelect = onMode,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Dnevni prikaz: mreža sati, kolona po frizeru, termini i blokade u bojama statusa. */
@Composable
private fun DayGrid(
    data: CalendarData,
    date: LocalDate,
    now: Instant,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onEmptyTap: (stylistId: String, start: Instant) -> Unit,
) {
    val colors = TerminTheme.colors
    val visible = remember(data.appointments, now) { data.appointments.filter { visibleInCalendar(it, now) } }
    val hours = remember(data, date, visible) { hourRange(data.salon, data.stylists, date, visible) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val density = LocalDensity.current
    // Mreža raste sa sistemskom veličinom slova, da bi sati i imena ostali čitljivi.
    val hourHeight = BaseHourHeight * density.fontScale.coerceIn(1f, MAX_GRID_SCALE)
    val timeColumnWidth = BaseTimeColumnWidth * density.fontScale.coerceIn(1f, MAX_TIME_SCALE)
    val isToday = now.atZone(BelgradeZone).toLocalDate() == date
    LaunchedEffect(date, hours.first) {
        val hour = if (isToday) now.atZone(BelgradeZone).toLocalTime().hour - 1 else hours.first
        val px = with(density) { (hourHeight * (hour - hours.first).coerceAtLeast(0)).toPx() }
        vertical.scrollTo(px.toInt())
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxWidth - timeColumnWidth
        val columnWidth = maxOf(MinColumnWidth, available / data.stylists.size)
        val scrollable = columnWidth * data.stylists.size > available
        val rowModifier = if (scrollable) Modifier.horizontalScroll(horizontal) else Modifier
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(timeColumnWidth))
                Row(rowModifier) {
                    data.stylists.forEach { stylist ->
                        Row(
                            Modifier
                                .width(columnWidth)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Avatar(LocalImageUrls.current.url(stylist.photoPath, ImageSize.Thumb), stylist.name, size = 28.dp)
                            Text(stylist.name, style = TerminTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TerminDivider()
            Row(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vertical)
                    .padding(bottom = bottomPadding),
            ) {
                TimeColumn(hours, hourHeight, timeColumnWidth)
                Row(rowModifier) {
                    data.stylists.forEach { stylist ->
                        StylistColumn(
                            stylist = stylist,
                            data = data,
                            date = date,
                            hours = hours,
                            appointments = visible.filter { it.stylistId == stylist.id },
                            timeOff = data.timeOff.filter { it.stylistId == stylist.id },
                            now = now.takeIf { isToday },
                            width = columnWidth,
                            hourHeight = hourHeight,
                            onOpen = onOpen,
                            onEmptyTap = { start -> onEmptyTap(stylist.id, start) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(hours: IntRange, hourHeight: Dp, width: Dp) {
    val colors = TerminTheme.colors
    Column(Modifier.width(width)) {
        hours.forEach { hour ->
            Box(Modifier.height(hourHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Text("%02d:00".format(hour), style = TerminTheme.type.caption, color = colors.textSecondary, maxLines = 1, modifier = Modifier.offset(y = (-2).dp))
            }
        }
    }
}

private fun minutesFrom(start: Int, instant: Instant): Float {
    val local = instant.atZone(BelgradeZone)
    return ((local.hour - start) * 60 + local.minute).toFloat()
}

private fun workingRanges(stylist: Stylist, data: CalendarData, date: LocalDate): List<TimeRange> =
    (stylist.workingHours ?: data.salon.workingHours).on(date.dayOfWeek)

@Composable
private fun StylistColumn(
    stylist: Stylist,
    data: CalendarData,
    date: LocalDate,
    hours: IntRange,
    appointments: List<Appointment>,
    timeOff: List<TimeOff>,
    now: Instant?,
    width: Dp,
    hourHeight: Dp,
    onOpen: (String) -> Unit,
    onEmptyTap: (Instant) -> Unit,
) {
    val colors = TerminTheme.colors
    val f = formatter
    val density = LocalDensity.current
    val minutePx = with(density) { hourHeight.toPx() } / 60f
    val working = workingRanges(stylist, data, date)
    val breaks = stylist.breaks.on(date.dayOfWeek)
    val dayStart = date.atStartOfDay(BelgradeZone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(BelgradeZone).toInstant()
    val divider = colors.divider
    val closed = colors.surfaceMuted
    Box(
        Modifier
            .width(width)
            .height(hourHeight * hours.count())
            .drawBehind {
                // Van radnog vremena i pauze: prigušena pozadina.
                val total = hours.count() * 60f
                val open = working.map { (it.start.hour - hours.first) * 60f + it.start.minute to (it.end.hour - hours.first) * 60f + it.end.minute }
                var cursor = 0f
                open.sortedBy { it.first }.forEach { (s, e) ->
                    if (s > cursor) drawRect(closed, Offset(0f, cursor * minutePx), Size(size.width, (s - cursor) * minutePx))
                    cursor = maxOf(cursor, e)
                }
                if (cursor < total) drawRect(closed, Offset(0f, cursor * minutePx), Size(size.width, (total - cursor) * minutePx))
                breaks.forEach { b ->
                    val s = (b.start.hour - hours.first) * 60f + b.start.minute
                    val e = (b.end.hour - hours.first) * 60f + b.end.minute
                    drawRect(closed, Offset(0f, s * minutePx), Size(size.width, (e - s) * minutePx))
                }
                for (i in 0..hours.count()) {
                    val y = i * 60f * minutePx
                    drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawLine(divider, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            }
            .pointerInput(date, hours.first) {
                detectTapGestures { offset ->
                    val minutes = (offset.y / minutePx).toInt()
                    val snapped = minutes - minutes % SNAP_MINUTES
                    val time = LocalTime.of(hours.first, 0).plusMinutes(snapped.toLong())
                    onEmptyTap(date.atTime(time).atZone(BelgradeZone).toInstant())
                }
            },
    ) {
        timeOff.forEach { off ->
            val start = maxOf(off.start, dayStart)
            val end = minOf(off.end, dayEnd)
            val top = minutesFrom(hours.first, start).coerceAtLeast(0f)
            val bottom = if (end == dayEnd) hours.count() * 60f else minutesFrom(hours.first, end).coerceAtMost(hours.count() * 60f)
            if (bottom > top) {
                Box(
                    Modifier
                        .offset(y = with(density) { (top * minutePx).toDp() })
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth()
                        .height(with(density) { ((bottom - top) * minutePx).toDp() })
                        .clip(TerminTheme.radii.smallShape)
                        .background(colors.surfaceRaised)
                        .padding(6.dp),
                ) {
                    Column {
                        Text(timeOffKindLabel(off.kind), style = TerminTheme.type.caption, color = colors.textSecondary)
                        if (off.reason.isNotBlank()) Text(off.reason, style = TerminTheme.type.caption, color = colors.textSecondary, maxLines = 2)
                    }
                }
            }
        }
        appointments.forEach { a ->
            val top = minutesFrom(hours.first, a.start)
            val height = (a.durationMin.toFloat()).coerceAtLeast(20f)
            val (bg, fg) = statusColors(a.status)
            val services = a.services.joinToString(", ") { it.name }
            val description = listOf(f.timeRange(a.start, a.end), a.customerName, services, f.status(a.status)).joinToString(". ")
            Box(
                Modifier
                    .offset(y = with(density) { (top * minutePx).toDp() })
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .fillMaxWidth()
                    .height(with(density) { (height * minutePx).toDp() } - 2.dp)
                    .clip(TerminTheme.radii.smallShape)
                    .background(bg)
                    .clickable { onOpen(a.id) }
                    .semantics { contentDescription = description }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (a.durationMin < ONE_LINE_MINUTES) {
                    // Kratak termin: vreme i ime u jednom redu.
                    Text("${f.time(a.start)} ${a.customerName}", style = TerminTheme.type.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Column {
                        Text(f.time(a.start), style = TerminTheme.type.caption, color = fg)
                        Text(a.customerName, style = TerminTheme.type.bodyStrong, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(services, style = TerminTheme.type.caption, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (now != null) {
            val y = minutesFrom(hours.first, now)
            if (y in 0f..hours.count() * 60f) {
                Box(
                    Modifier
                        .offset(y = with(density) { (y * minutePx).toDp() })
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(colors.error),
                )
            }
        }
    }
}

/** Nedeljni prikaz: dan po dan, termini kao lista (čitljivo i na telefonu). */
@Composable
private fun WeekAgenda(
    data: CalendarData,
    days: List<LocalDate>,
    today: LocalDate,
    now: Instant,
    showStylist: Boolean,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    val byDay = remember(data.appointments, now) {
        data.appointments.filter { visibleInCalendar(it, now) }.groupBy { it.start.atZone(BelgradeZone).toLocalDate() }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, top = spacing.xs, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(days, key = { it.toString() }) { day ->
            val list = byDay[day].orEmpty().sortedBy { it.start }
            val open = data.salon.workingHours.on(day.dayOfWeek).isNotEmpty()
            TerminCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDay(day) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.relativeDay(day, today),
                                style = TerminTheme.type.bodyStrong,
                                color = if (day == today) colors.accentText else colors.text,
                                modifier = Modifier.semantics { heading() },
                            )
                            val revenue = list.sumOf { it.totalPrice }
                            Text(
                                if (list.isEmpty()) {
                                    stringResource(if (open) R.string.calendar_empty_day else R.string.calendar_closed)
                                } else {
                                    pluralStringResource(R.plurals.calendar_week_count, list.size, list.size, f.price(revenue))
                                },
                                style = TerminTheme.type.caption,
                                color = colors.textSecondary,
                            )
                        }
                        TerminIconImage(TerminIcon.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                    }
                    list.forEach { a ->
                        TerminDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(a.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(f.time(a.start), style = TerminTheme.type.bodyStrong, color = colors.text, modifier = Modifier.width(52.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.customerName, style = TerminTheme.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val detail = listOfNotNull(a.stylistName.takeIf { showStylist }, a.services.joinToString(", ") { it.name }).joinToString(" · ")
                                Text(detail, style = TerminTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            StatusPill(a.status)
                        }
                    }
                }
            }
        }
    }
}
