package rs.termin.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.SectionHeader
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.UserRepository
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.SalonCard
import rs.termin.app.core.ui.loadable
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class FavoriteStylist(val key: String, val stylist: Stylist, val salonName: String)

data class Favorites(val salons: List<Salon>, val stylists: List<FavoriteStylist>) {
    val isEmpty: Boolean get() = salons.isEmpty() && stylists.isEmpty()
}

data class FavoritesUiState(val favorites: Loadable<Favorites> = Loadable.Loading, val now: Instant = Instant.EPOCH)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val users: UserRepository,
    private val salons: SalonRepository,
    clock: Clock,
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    private val keys = users.observeProfile().catch { emit(null) }
        .map { (it?.favoriteSalonIds ?: emptySet()) to (it?.favoriteStylistKeys ?: emptySet()) }
        .distinctUntilChanged()

    val uiState: StateFlow<FavoritesUiState> = combine(keys, retry) { k, _ -> k }
        .flatMapLatest { (salonIds, stylistKeys) -> flow { emit(load(salonIds, stylistKeys)) } }
        .map { FavoritesUiState(it, clock.instant()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), FavoritesUiState())

    private suspend fun load(salonIds: Set<String>, stylistKeys: Set<String>): Loadable<Favorites> = loadable {
        val stylistPairs = stylistKeys.mapNotNull { key -> key.split('/').takeIf { it.size == 2 }?.let { Triple(key, it[0], it[1]) } }
        val allSalons = salons.salonsByIds(salonIds + stylistPairs.map { it.second }).associateBy { it.id }
        val stylists = stylistPairs.groupBy { it.second }.flatMap { (salonId, pairs) ->
            val list = salons.observeStylists(salonId).first().associateBy { it.id }
            pairs.mapNotNull { (key, _, stylistId) ->
                val stylist = list[stylistId] ?: return@mapNotNull null
                FavoriteStylist(key, stylist, allSalons[salonId]?.name.orEmpty())
            }
        }
        Favorites(salonIds.mapNotNull { allSalons[it] }.sortedBy { it.name }, stylists.sortedBy { it.stylist.name })
    }

    fun removeSalon(id: String) {
        viewModelScope.launch { runCatching { users.toggleFavoriteSalon(id, false) } }
    }

    fun removeStylist(key: String) {
        viewModelScope.launch { runCatching { users.toggleFavoriteStylist(key, false) } }
    }

    fun retry() {
        retry.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenSalon: (String) -> Unit,
    onBookStylist: (salonId: String, stylistId: String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = TerminTheme.spacing
    val images = LocalImageUrls.current
    Scaffold(
        containerColor = TerminTheme.colors.background,
        topBar = { TerminTopBar(stringResource(R.string.favorites_title), onBack = onBack) },
    ) { padding ->
        when (val favorites = state.favorites) {
            Loadable.Loading -> LoadingSkeleton(Modifier.padding(padding).padding(spacing.gutter)) {
                SkeletonBox(Modifier.fillMaxWidth().height(220.dp), TerminTheme.radii.cardShape)
            }
            is Loadable.Failed -> ErrorState(viewModel::retry, Modifier.padding(padding))
            is Loadable.Ready -> {
                val data = favorites.value
                if (data.isEmpty) {
                    EmptyState(
                        Illustration.Heart,
                        stringResource(R.string.favorites_empty_title),
                        stringResource(R.string.favorites_empty),
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + spacing.sm, bottom = padding.calculateBottomPadding() + spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        if (data.salons.isNotEmpty()) {
                            item(key = "salons", contentType = "header") { SectionHeader(stringResource(R.string.favorites_salons)) }
                            items(data.salons, key = { "s-${it.id}" }, contentType = { "salon" }) { salon ->
                                SalonCard(
                                    salon = salon,
                                    photoUrl = images.url(salon.coverPhoto ?: salon.photos.firstOrNull()),
                                    distanceKm = null,
                                    now = state.now,
                                    onClick = { onOpenSalon(salon.id) },
                                    favorite = true,
                                    onToggleFavorite = { viewModel.removeSalon(salon.id) },
                                    modifier = Modifier.padding(horizontal = spacing.gutter).animateItem(),
                                )
                            }
                        }
                        if (data.stylists.isNotEmpty()) {
                            item(key = "stylists", contentType = "header") { SectionHeader(stringResource(R.string.favorites_stylists)) }
                            items(data.stylists, key = { "p-${it.key}" }, contentType = { "stylist" }) { favorite ->
                                TerminCard(Modifier.padding(horizontal = spacing.gutter).fillMaxWidth().animateItem()) {
                                    TerminListItem(
                                        favorite.stylist.name,
                                        subtitle = favorite.salonName,
                                        leading = { Avatar(images.url(favorite.stylist.photoPath), favorite.stylist.name) },
                                        trailing = {
                                            TerminIconButton(
                                                TerminIcon.FavoriteFilled,
                                                stringResource(R.string.cd_favorite_remove),
                                                { viewModel.removeStylist(favorite.key) },
                                                tint = TerminTheme.colors.accentText,
                                            )
                                        },
                                        onClick = { onBookStylist(favorite.stylist.salonId, favorite.stylist.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
