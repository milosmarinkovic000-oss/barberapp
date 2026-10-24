package rs.termin.app.feature.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import rs.termin.app.BuildConfig
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.ChipRow
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.AvailabilityFilter
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.model.SearchFilters
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.SalonCard
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.isWideLayout
import rs.termin.app.feature.home.icon

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenSalon: (String) -> Unit,
    detailPane: @Composable (salonId: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wide = isWideLayout()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onLocationGranted()
    }
    SearchContent(
        state = state,
        contentPadding = contentPadding,
        wide = wide,
        onQuery = viewModel::onQuery,
        onSubmit = viewModel::submitQuery,
        onRecent = viewModel::useRecent,
        onApplyFilters = viewModel::applyFilters,
        onResetFilters = viewModel::resetFilters,
        onView = viewModel::setView,
        onRetry = viewModel::search,
        onRequestLocation = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onFocusConsumed = viewModel::focusConsumed,
        onOpenSalon = { id -> if (wide) viewModel.select(id) else onOpenSalon(id) },
        detailPane = detailPane,
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    contentPadding: PaddingValues,
    wide: Boolean,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onRecent: (String) -> Unit,
    onApplyFilters: (SearchFilters) -> Unit,
    onResetFilters: () -> Unit,
    onView: (SearchView) -> Unit,
    onRetry: () -> Unit,
    onRequestLocation: () -> Unit,
    onFocusConsumed: () -> Unit,
    onOpenSalon: (String) -> Unit,
    detailPane: @Composable (String) -> Unit,
) {
    val colors = TerminTheme.colors
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(Modifier.weight(if (wide) 0.45f else 1f).fillMaxHeight()) {
            SearchHeader(state, onQuery, onSubmit, onView, onOpenFilters = { showFilters = true }, onFocusConsumed = onFocusConsumed)
            Results(state, contentPadding, onRecent, onResetFilters, onRetry, onOpenSalon)
        }
        if (wide) {
            VerticalDivider(color = colors.divider)
            Box(Modifier.weight(0.55f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val selected = state.selectedSalonId
                if (selected == null) {
                    EmptyState(Illustration.Search, stringResource(R.string.search_title), stringResource(R.string.search_select_salon))
                } else {
                    detailPane(selected)
                }
            }
        }
    }
    if (showFilters) {
        FiltersSheet(
            initial = state.filters,
            hasLocation = state.location != null,
            onRequestLocation = onRequestLocation,
            onApply = {
                onApplyFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SearchHeader(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onView: (SearchView) -> Unit,
    onOpenFilters: () -> Unit,
    onFocusConsumed: () -> Unit,
) {
    val spacing = TerminTheme.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.focusSearch) {
        if (state.focusSearch) {
            focus.requestFocus()
            onFocusConsumed()
        }
    }
    Column(Modifier.padding(horizontal = spacing.gutter, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        TerminTextField(
            value = state.filters.query,
            onValueChange = onQuery,
            label = stringResource(R.string.search_field),
            leadingIcon = TerminIcon.Search,
            trailing = if (state.filters.query.isNotEmpty()) {
                { TerminIconButton(TerminIcon.Close, stringResource(R.string.search_clear), { onQuery("") }) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.focusRequester(focus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            TerminButton(
                text = if (state.activeFilterCount > 0) stringResource(R.string.search_filters_active, state.activeFilterCount) else stringResource(R.string.search_filters),
                onClick = onOpenFilters,
                variant = if (state.activeFilterCount > 0) ButtonVariant.Tonal else ButtonVariant.Secondary,
                icon = TerminIcon.Tune,
            )
            if (TerminTheme.features.map) {
                SegmentedControl(
                    options = SearchView.entries,
                    selected = state.view,
                    label = { stringResource(if (it == SearchView.List) R.string.search_view_list else R.string.search_view_map) },
                    onSelect = onView,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Results(
    state: SearchUiState,
    contentPadding: PaddingValues,
    onRecent: (String) -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    onOpenSalon: (String) -> Unit,
) {
    val spacing = TerminTheme.spacing
    val bottom = contentPadding.calculateBottomPadding() + spacing.xl
    when (val results = state.results) {
        Loadable.Loading -> Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(220.dp), shape = TerminTheme.radii.cardShape) }
        }
        is Loadable.Failed -> ErrorState(onRetry, Modifier.verticalScroll(rememberScrollState()))
        is Loadable.Ready -> {
            if (results.value.isEmpty()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyState(
                        Illustration.Search,
                        stringResource(R.string.search_empty_title),
                        stringResource(R.string.search_empty_message),
                        actionLabel = if (state.activeFilterCount > 0) stringResource(R.string.search_reset_filters) else null,
                        onAction = if (state.activeFilterCount > 0) onResetFilters else null,
                    )
                }
            } else if (state.view == SearchView.Map && TerminTheme.features.map) {
                MapResults(results.value, state, onOpenSalon, bottom)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    if (state.filters.query.isEmpty() && state.recent.isNotEmpty()) {
                        item(key = "recent", contentType = "recent") { RecentSearches(state.recent, onRecent) }
                    }
                    item(key = "count", contentType = "count") {
                        Text(
                            pluralStringResource(R.plurals.search_results, results.value.size, results.value.size),
                            style = TerminTheme.type.callout,
                            color = TerminTheme.colors.textSecondary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    items(results.value, key = { it.salon.id }, contentType = { "salon" }) { item ->
                        ResultCard(item, state, onOpenSalon)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(item: SalonListItem, state: SearchUiState, onOpenSalon: (String) -> Unit) {
    val images = LocalImageUrls.current
    SalonCard(
        salon = item.salon,
        photoUrl = images.url(item.salon.coverPhoto, ImageSize.Thumb),
        distanceKm = item.distanceKm,
        now = state.now,
        onClick = { onOpenSalon(item.salon.id) },
    )
}

@Composable
private fun RecentSearches(recent: List<String>, onRecent: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.search_recent), style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            recent.take(3).forEach { query -> TerminChip(query, selected = false, onClick = { onRecent(query) }, icon = TerminIcon.History) }
        }
    }
}

/** Mapa sa salonima; bez Maps ključa: poruka, lista i „Otvori u Google Mapama“. */
@Composable
private fun MapResults(items: List<SalonListItem>, state: SearchUiState, onOpenSalon: (String) -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val spacing = TerminTheme.spacing
    val context = LocalContext.current
    if (!BuildConfig.HAS_MAPS_KEY) {
        LazyColumn(
            contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "no-key") { Notice(stringResource(R.string.search_map_no_key), icon = TerminIcon.Map) }
            items(items, key = { it.salon.id }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultCard(item, state, onOpenSalon)
                    TerminButton(
                        stringResource(R.string.action_open_maps),
                        { Actions.openInMaps(context, item.salon.location, "${item.salon.name}, ${item.salon.address}") },
                        variant = ButtonVariant.Secondary,
                        icon = TerminIcon.Map,
                    )
                }
            }
        }
        return
    }
    val first = items.firstNotNullOfOrNull { it.salon.location } ?: state.location
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(first?.latitude ?: BELGRADE_LAT, first?.longitude ?: BELGRADE_LNG), DEFAULT_ZOOM)
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize().padding(bottom = bottom),
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false),
    ) {
        items.forEach { item ->
            val location = item.salon.location ?: return@forEach
            key(item.salon.id) {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(location.latitude, location.longitude)),
                    title = item.salon.name,
                    snippet = item.salon.address,
                    onInfoWindowClick = { onOpenSalon(item.salon.id) },
                )
            }
        }
    }
}

@Composable
private fun FiltersSheet(
    initial: SearchFilters,
    hasLocation: Boolean,
    onRequestLocation: () -> Unit,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var filters by remember { mutableStateOf(initial) }
    val f = formatter
    val spacing = TerminTheme.spacing
    TerminBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(stringResource(R.string.search_filters), style = TerminTheme.type.title2, modifier = Modifier.padding(horizontal = spacing.gutter))
            FilterGroup(stringResource(R.string.search_filter_service)) {
                TerminChip(stringResource(R.string.search_any), filters.category == null, { filters = filters.copy(category = null) })
                ServiceCategory.entries.forEach { c ->
                    TerminChip(f.category(c), filters.category == c, { filters = filters.copy(category = c) }, icon = c.icon())
                }
            }
            FilterGroup(stringResource(R.string.search_filter_price)) {
                TerminChip(stringResource(R.string.search_any), filters.maxPrice == null, { filters = filters.copy(maxPrice = null) })
                PRICE_STEPS.forEach { p -> TerminChip(f.price(p), filters.maxPrice == p, { filters = filters.copy(maxPrice = p) }) }
            }
            FilterGroup(stringResource(R.string.search_filter_rating)) {
                TerminChip(stringResource(R.string.search_any), filters.minRating == null, { filters = filters.copy(minRating = null) })
                RATING_STEPS.forEach { r ->
                    TerminChip(stringResource(R.string.search_rating_at_least, f.rating(r)), filters.minRating == r, { filters = filters.copy(minRating = r) }, icon = TerminIcon.Star)
                }
            }
            FilterGroup(stringResource(R.string.search_filter_distance)) {
                TerminChip(stringResource(R.string.search_any), filters.maxDistanceKm == null, { filters = filters.copy(maxDistanceKm = null) })
                DISTANCE_STEPS.forEach { d ->
                    TerminChip(
                        stringResource(R.string.search_distance_up_to, f.distance(d)),
                        filters.maxDistanceKm == d,
                        {
                            if (!hasLocation) onRequestLocation()
                            filters = filters.copy(maxDistanceKm = d)
                        },
                        icon = TerminIcon.NearMe,
                    )
                }
            }
            if (filters.maxDistanceKm != null && !hasLocation) {
                Notice(stringResource(R.string.search_distance_needs_location), Modifier.padding(horizontal = spacing.gutter))
            }
            FilterGroup(stringResource(R.string.search_filter_availability)) {
                AvailabilityFilter.entries.forEach { a ->
                    val label = when (a) {
                        AvailabilityFilter.Any -> stringResource(R.string.search_any_time)
                        AvailabilityFilter.Today -> stringResource(R.string.search_today)
                        AvailabilityFilter.Tomorrow -> stringResource(R.string.search_tomorrow)
                    }
                    TerminChip(label, filters.availability == a, { filters = filters.copy(availability = a) })
                }
            }
            Row(Modifier.padding(horizontal = spacing.gutter), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                TerminButton(stringResource(R.string.search_reset_filters), { filters = SearchFilters(query = initial.query) }, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
                TerminButton(stringResource(R.string.search_apply), { onApply(filters) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TerminTheme.type.title3, modifier = Modifier.padding(horizontal = TerminTheme.spacing.gutter))
        ChipRow { content() }
    }
}

private val PRICE_STEPS = listOf(1_000, 2_000, 3_500, 6_000)
private val RATING_STEPS = listOf(4.0, 4.5)
private val DISTANCE_STEPS = listOf(1.0, 3.0, 5.0, 10.0)
private const val BELGRADE_LAT = 44.8125
private const val BELGRADE_LNG = 20.4612
private const val DEFAULT_ZOOM = 12.5f
