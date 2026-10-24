package rs.termin.app.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.ChipRow
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.OfflineBanner
import rs.termin.app.core.designsystem.component.SectionHeader
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.SalonCard
import rs.termin.app.core.ui.TestTags
import rs.termin.app.core.ui.formatter

fun ServiceCategory.icon(): TerminIcon = when (this) {
    ServiceCategory.Haircut -> TerminIcon.ContentCut
    ServiceCategory.Blowdry -> TerminIcon.Air
    ServiceCategory.Coloring -> TerminIcon.Palette
    ServiceCategory.Highlights -> TerminIcon.Brush
    ServiceCategory.Beard -> TerminIcon.Face
    ServiceCategory.Treatment -> TerminIcon.Spa
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenSalon: (String) -> Unit,
    onSearch: (category: String?, freeToday: Boolean) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.loadNearby() else viewModel.permissionDenied()
    }
    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onOpenSalon = onOpenSalon,
        onSearch = onSearch,
        onRequestLocation = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onToggleFavorite = viewModel::toggleFavorite,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenSalon: (String) -> Unit,
    onSearch: (category: String?, freeToday: Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val images = LocalImageUrls.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.HOME_LIST)
            .background(colors.background),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + spacing.md,
            bottom = contentPadding.calculateBottomPadding() + spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (!state.online) fullWidth("offline") { OfflineBanner() }
        fullWidth("header") { Header(state.firstName, onSearch) }
        fullWidth("categories") { Categories(onSearch) }
        fullWidth("free-today") { FreeToday(state, onOpenSalon, onSearch, onToggleFavorite) }
        fullWidth("nearby") { Nearby(state, onOpenSalon, onRequestLocation, onToggleFavorite) }
        fullWidth("all-header") { SectionHeader(stringResource(R.string.home_all_salons), Modifier.padding(top = spacing.sm)) }
        when (val salons = state.salons) {
            Loadable.Loading -> items(3, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                SkeletonBox(
                    Modifier
                        .padding(horizontal = spacing.gutter)
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = TerminTheme.radii.cardShape,
                )
            }
            is Loadable.Failed -> fullWidth("error") { ErrorState(onRetry) }
            is Loadable.Ready -> {
                if (salons.value.isEmpty()) {
                    fullWidth("empty") {
                        EmptyState(Illustration.Scissors, stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_message))
                    }
                }
                items(salons.value, key = { it.id }, contentType = { "salon" }) { salon ->
                    SalonCard(
                        salon = salon,
                        photoUrl = images.url(salon.coverPhoto, ImageSize.Thumb),
                        distanceKm = null,
                        now = state.now,
                        onClick = { onOpenSalon(salon.id) },
                        favorite = salon.id in state.favorites,
                        onToggleFavorite = { onToggleFavorite(salon.id) },
                        modifier = Modifier.padding(horizontal = spacing.gutter),
                    )
                }
                if (state.canLoadMore) {
                    fullWidth("more") {
                        TerminButton(
                            stringResource(R.string.home_load_more),
                            onLoadMore,
                            variant = ButtonVariant.Secondary,
                            loading = state.loadingMore,
                            modifier = Modifier.padding(horizontal = spacing.gutter),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

@Composable
private fun Header(firstName: String, onSearch: (String?, Boolean) -> Unit) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            if (firstName.isBlank()) stringResource(R.string.home_greeting_anonymous) else stringResource(R.string.home_greeting, firstName),
            style = TerminTheme.type.display,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.home_subtitle), style = TerminTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(spacing.xs))
        Surface(
            onClick = { onSearch(null, false) },
            shape = TerminTheme.radii.buttonShape,
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { role = Role.Button },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TerminIconImage(TerminIcon.Search, contentDescription = null, tint = colors.textSecondary)
                Text(stringResource(R.string.home_search_hint), style = TerminTheme.type.body, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun Categories(onSearch: (String?, Boolean) -> Unit) {
    val f = formatter
    Column(verticalArrangement = Arrangement.spacedBy(TerminTheme.spacing.xs)) {
        Text(
            stringResource(R.string.home_categories),
            style = TerminTheme.type.title3,
            color = TerminTheme.colors.text,
            modifier = Modifier
                .padding(horizontal = TerminTheme.spacing.gutter)
                .semantics { heading() },
        )
        ChipRow {
            ServiceCategory.entries.forEach { category ->
                TerminChip(f.category(category), selected = false, onClick = { onSearch(category.key, false) }, icon = category.icon())
            }
        }
    }
}

@Composable
private fun FreeToday(state: HomeUiState, onOpenSalon: (String) -> Unit, onSearch: (String?, Boolean) -> Unit, onToggleFavorite: (String) -> Unit) {
    val spacing = TerminTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader(
            stringResource(R.string.home_free_today),
            subtitle = stringResource(R.string.home_free_today_subtitle),
            actionLabel = stringResource(R.string.action_see_all),
            onAction = { onSearch(null, true) },
        )
        when (val free = state.freeToday) {
            Loadable.Loading -> HorizontalSkeletons()
            is Loadable.Failed -> Notice(stringResource(R.string.state_error_message), Modifier.padding(horizontal = spacing.gutter))
            is Loadable.Ready -> if (free.value.isEmpty()) {
                Notice(stringResource(R.string.home_free_today_empty), Modifier.padding(horizontal = spacing.gutter))
            } else {
                SalonRow(free.value, state, onOpenSalon, onToggleFavorite, distances = emptyMap())
            }
        }
    }
}

@Composable
private fun Nearby(state: HomeUiState, onOpenSalon: (String) -> Unit, onRequestLocation: () -> Unit, onToggleFavorite: (String) -> Unit) {
    val spacing = TerminTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader(stringResource(R.string.home_nearby))
        when (val nearby = state.nearby) {
            NearbyState.NeedsPermission -> Column(
                Modifier.padding(horizontal = spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Notice(stringResource(R.string.home_nearby_permission), icon = TerminIcon.MyLocation)
                TerminButton(stringResource(R.string.home_nearby_allow), onRequestLocation, variant = ButtonVariant.Secondary, icon = TerminIcon.NearMe)
            }
            NearbyState.Loading -> HorizontalSkeletons()
            NearbyState.Unavailable -> Notice(stringResource(R.string.home_nearby_unavailable), Modifier.padding(horizontal = spacing.gutter))
            is NearbyState.Ready -> if (nearby.items.isEmpty()) {
                Notice(stringResource(R.string.home_nearby_empty), Modifier.padding(horizontal = spacing.gutter))
            } else {
                SalonRow(nearby.items.map { it.salon }, state, onOpenSalon, onToggleFavorite, nearby.items.associate { it.salon.id to it.distanceKm })
            }
        }
    }
}

@Composable
private fun SalonRow(
    salons: List<Salon>,
    state: HomeUiState,
    onOpenSalon: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    distances: Map<String, Double?>,
) {
    val images = LocalImageUrls.current
    val spacing = TerminTheme.spacing
    LazyRow(
        contentPadding = PaddingValues(horizontal = spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(salons, key = { it.id }, contentType = { "salon" }) { salon ->
            SalonCard(
                salon = salon,
                photoUrl = images.url(salon.coverPhoto, ImageSize.Thumb),
                distanceKm = distances[salon.id],
                now = state.now,
                onClick = { onOpenSalon(salon.id) },
                favorite = salon.id in state.favorites,
                onToggleFavorite = { onToggleFavorite(salon.id) },
                modifier = Modifier.width(300.dp),
            )
        }
    }
}

@Composable
private fun HorizontalSkeletons() {
    val spacing = TerminTheme.spacing
    Row(Modifier.padding(horizontal = spacing.gutter), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        repeat(2) {
            SkeletonBox(
                Modifier
                    .width(300.dp)
                    .height(240.dp),
                shape = TerminTheme.radii.cardShape,
            )
        }
    }
}
