package rs.termin.app.feature.owner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.DayPoint
import rs.termin.app.core.domain.model.RankedItem
import rs.termin.app.core.domain.model.SalonStats
import rs.termin.app.core.domain.model.StatsPeriod
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.loadable
import rs.termin.app.feature.profile.FormScaffold
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.Week,
    val date: LocalDate = LocalDate.MIN,
    val stats: Loadable<SalonStats> = Loadable.Loading,
)

/** Početak perioda: dan, ponedeljak u nedelji ili prvi u mesecu. */
fun periodStart(period: StatsPeriod, date: LocalDate): LocalDate = when (period) {
    StatsPeriod.Day -> date
    StatsPeriod.Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    StatsPeriod.Month -> date.withDayOfMonth(1)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    auth: AuthRepository,
    private val staff: StaffRepository,
    clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(StatsUiState(date = LocalDate.now(clock.withZone(BelgradeZone))))
    private val retry = MutableStateFlow(0)
    private val salonId = auth.authState.map { it.staffSession()?.salonId }.distinctUntilChanged()

    private val stats = combine(salonId, local.map { it.period to periodStart(it.period, it.date) }.distinctUntilChanged(), retry) { id, p, _ -> id to p }
        .flatMapLatest { (id, p) ->
            if (id == null) {
                flowOf<Loadable<SalonStats>>(Loadable.Loading)
            } else {
                flow<Loadable<SalonStats>> {
                    emit(Loadable.Loading)
                    emit(loadable { staff.stats(id, p.first, p.second) })
                }
            }
        }

    val uiState: StateFlow<StatsUiState> = combine(local, stats) { l, s -> l.copy(stats = s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun setPeriod(period: StatsPeriod) = local.update { it.copy(period = period) }

    fun previous() = local.update { it.copy(date = shift(it, -1)) }

    fun next() = local.update { it.copy(date = shift(it, 1)) }

    private fun shift(s: StatsUiState, by: Long): LocalDate = when (s.period) {
        StatsPeriod.Day -> s.date.plusDays(by)
        StatsPeriod.Week -> s.date.plusWeeks(by)
        StatsPeriod.Month -> s.date.plusMonths(by)
    }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = formatter
    val colors = TerminTheme.colors
    FormScaffold(stringResource(R.string.stats_title), onBack) {
        SegmentedControl(
            options = StatsPeriod.entries,
            selected = state.period,
            label = {
                when (it) {
                    StatsPeriod.Day -> stringResource(R.string.calendar_day)
                    StatsPeriod.Week -> stringResource(R.string.calendar_week)
                    StatsPeriod.Month -> f.monthName(state.date).replaceFirstChar(Char::uppercase)
                }
            },
            onSelect = viewModel::setPeriod,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminIconButton(TerminIcon.ChevronLeft, stringResource(R.string.calendar_prev), viewModel::previous)
            val start = periodStart(state.period, state.date)
            Text(
                when (state.period) {
                    StatsPeriod.Day -> f.dayLong(start)
                    StatsPeriod.Week -> stringResource(R.string.stats_week_of, f.dayMonth(start))
                    StatsPeriod.Month -> "${f.monthName(start).replaceFirstChar(Char::uppercase)} ${start.year}"
                },
                style = TerminTheme.type.bodyStrong,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            TerminIconButton(TerminIcon.ChevronRight, stringResource(R.string.calendar_next), viewModel::next)
        }
        when (val loaded = state.stats) {
            Loadable.Loading -> LoadingSkeleton { SkeletonBox(Modifier.fillMaxWidth().height(320.dp), TerminTheme.radii.cardShape) }
            is Loadable.Failed -> ErrorState(viewModel::retry)
            is Loadable.Ready -> StatsBody(loaded.value, state.period)
        }
    }
}

@Composable
private fun StatsBody(stats: SalonStats, period: StatsPeriod) {
    val f = formatter
    if (stats.total == 0) {
        Notice(stringResource(R.string.stats_empty))
        return
    }
    // Glavni broj perioda (jedan po prikazu), pa prateće pločice.
    TerminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.stats_revenue), style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
            Text(f.price(stats.revenueEstimate), style = TerminTheme.type.number.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold), color = TerminTheme.colors.text)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.stats_total), f.number(stats.total), Modifier.weight(1f))
        StatTile(stringResource(R.string.stats_completed), f.number(stats.completed), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.stats_canceled), f.number(stats.canceled), Modifier.weight(1f))
        StatTile(
            stringResource(R.string.stats_no_show_rate),
            stringResource(R.string.stats_percent, f.decimal(stats.noShowRate * 100, 1)),
            Modifier.weight(1f),
            detail = "${f.number(stats.noShow)} · ${stringResource(R.string.stats_no_show)}",
        )
    }
    if (period != StatsPeriod.Day && stats.series.isNotEmpty()) {
        Overline(stringResource(R.string.stats_series))
        TerminCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp)) { BarChart(stats.series, period) }
        }
    }
    if (stats.topServices.isNotEmpty()) {
        Overline(stringResource(R.string.stats_top_services))
        RankedList(stats.topServices)
    }
    if (stats.topStylists.isNotEmpty()) {
        Overline(stringResource(R.string.stats_top_stylists))
        RankedList(stats.topStylists)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = TerminTheme.colors
    // Pločica se čita kao celina (naziv, vrednost, detalj).
    TerminCard(modifier.semantics(mergeDescendants = true) { }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = TerminTheme.type.callout, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = TerminTheme.type.number.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
            if (detail != null) Text(detail, style = TerminTheme.type.caption, color = colors.textSecondary)
        }
    }
}

/** Zaokružuje vrh ose na „čist“ broj (1, 2, 5 × 10ⁿ). */
fun niceCeiling(value: Int): Int {
    if (value <= 0) return 1
    var magnitude = 1
    while (magnitude * 10 <= value) magnitude *= 10
    return listOf(1, 2, 5, 10).map { it * magnitude }.first { it >= value }
}

/**
 * Stubičasti grafikon jedne serije (termini po danima): stubovi do 24 dp, zaobljen vrh,
 * razmak 2 dp, tanke linije mreže; dodir na stub prikazuje datum i broj.
 */
@Composable
private fun BarChart(series: List<DayPoint>, period: StatsPeriod) {
    val colors = TerminTheme.colors
    val f = formatter
    val density = LocalDensity.current
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val max = niceCeiling(series.maxOf { it.count })
    val peak = series.indices.maxBy { series[it].count }
    val bar = colors.accentText
    val grid = colors.divider
    val resources = LocalResources.current
    val description = remember(series, resources) {
        series.joinToString("; ") { resources.getString(R.string.stats_cd_bar, f.dayShort(it.date), it.count) }
    }
    Column(Modifier.semantics { contentDescription = description }) {
        val shown = selected ?: peak
        val point = series[shown]
        Text(
            "${f.dayShort(point.date)} · ${point.count} · ${f.price(point.revenue)}",
            style = TerminTheme.type.callout,
            color = colors.text,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row {
            Column(Modifier.height(CHART_HEIGHT.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(max.toString(), style = TerminTheme.type.caption, color = colors.textSecondary)
                Text((max / 2).toString(), style = TerminTheme.type.caption, color = colors.textSecondary)
                Text("0", style = TerminTheme.type.caption, color = colors.textSecondary)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(CHART_HEIGHT.dp)
                    .pointerInput(series) {
                        detectTapGestures { offset ->
                            val slot = size.width / series.size.toFloat()
                            selected = (offset.x / slot).toInt().coerceIn(0, series.lastIndex)
                        }
                    },
            ) {
                val radius = with(density) { 4.dp.toPx() }
                val gap = with(density) { 2.dp.toPx() }
                val maxBar = with(density) { 24.dp.toPx() }
                val slot = size.width / series.size
                val width = minOf(maxBar, slot - gap)
                listOf(0f, 0.5f, 1f).forEach { t ->
                    val y = size.height * t
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                series.forEachIndexed { i, p ->
                    if (p.count == 0) return@forEachIndexed
                    val h = size.height * p.count / max
                    val left = i * slot + (slot - width) / 2
                    val top = size.height - h
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = top,
                                right = left + width,
                                bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius),
                                topRightCornerRadius = CornerRadius(radius),
                            ),
                        )
                    }
                    drawPath(path, if (selected == null || selected == i) bar else bar.copy(alpha = 0.45f))
                }
                drawRect(grid, Offset(0f, size.height - 1f), Size(size.width, 1f))
            }
        }
        Row(Modifier.padding(start = 24.dp, top = 4.dp)) {
            val step = if (period == StatsPeriod.Month) 7 else 1
            series.forEachIndexed { i, p ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % step == 0) {
                        Text(
                            if (period == StatsPeriod.Month) p.date.dayOfMonth.toString() else f.weekdayShort(p.date),
                            style = TerminTheme.type.caption,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedList(items: List<RankedItem>) {
    val colors = TerminTheme.colors
    val top = items.maxOf { it.count }.coerceAtLeast(1)
    TerminCard(Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) TerminDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(item.name, style = TerminTheme.type.body, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.count.toString(), style = TerminTheme.type.number, color = colors.text)
                }
                Box(
                    Modifier
                        .fillMaxWidth(item.count.toFloat() / top)
                        .height(6.dp)
                        .clip(TerminTheme.radii.smallShape)
                        .background(colors.accentText),
                )
            }
        }
    }
}

private const val CHART_HEIGHT = 160
