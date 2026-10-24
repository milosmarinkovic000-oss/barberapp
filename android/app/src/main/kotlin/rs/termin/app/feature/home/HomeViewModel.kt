package rs.termin.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.LocationProvider
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.UserRepository
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.loadable
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

sealed interface NearbyState {
    data object NeedsPermission : NearbyState
    data object Loading : NearbyState
    data object Unavailable : NearbyState
    data class Ready(val items: List<SalonListItem>) : NearbyState
}

data class HomeUiState(
    val firstName: String = "",
    val freeToday: Loadable<List<Salon>> = Loadable.Loading,
    val nearby: NearbyState = NearbyState.NeedsPermission,
    val salons: Loadable<List<Salon>> = Loadable.Loading,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val online: Boolean = true,
    val now: Instant = Instant.EPOCH,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val salons: SalonRepository,
    private val users: UserRepository,
    private val location: LocationProvider,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(HomeUiState(now = clock.instant()))
    private var cursor: Any? = null

    val uiState: StateFlow<HomeUiState> = combine(
        state,
        users.observeProfile().catch { emit(null) },
        connectivity.isOnline,
    ) { s, profile, online ->
        s.copy(
            firstName = profile?.name?.trim()?.substringBefore(' ').orEmpty(),
            favorites = profile?.favoriteSalonIds.orEmpty(),
            online = online,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), state.value)

    init {
        refresh()
    }

    fun refresh() {
        cursor = null
        state.update { it.copy(now = clock.instant(), freeToday = Loadable.Loading, salons = Loadable.Loading) }
        viewModelScope.launch { state.update { it.copy(freeToday = loadable { salons.freeToday() }) } }
        viewModelScope.launch {
            val page = loadable { salons.topSalons(PAGE_SIZE) }
            state.update {
                it.copy(
                    salons = when (page) {
                        is Loadable.Ready -> Loadable.Ready(page.value.items)
                        is Loadable.Failed -> page
                        Loadable.Loading -> Loadable.Loading
                    },
                    canLoadMore = page.valueOrNull()?.nextCursor != null,
                )
            }
            cursor = page.valueOrNull()?.nextCursor
        }
        if (location.hasPermission()) loadNearby()
    }

    /** Lista se učitava po 20 stavki. */
    fun loadMore() {
        val current = state.value.salons.valueOrNull() ?: return
        if (state.value.loadingMore || cursor == null) return
        state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = loadable { salons.topSalons(PAGE_SIZE, cursor) }
            cursor = page.valueOrNull()?.nextCursor
            state.update {
                it.copy(
                    salons = Loadable.Ready(current + page.valueOrNull()?.items.orEmpty()),
                    canLoadMore = cursor != null,
                    loadingMore = false,
                )
            }
        }
    }

    fun loadNearby() {
        if (!location.hasPermission()) {
            state.update { it.copy(nearby = NearbyState.NeedsPermission) }
            return
        }
        state.update { it.copy(nearby = NearbyState.Loading) }
        viewModelScope.launch {
            val here = location.currentLocation()
            val result = if (here == null) null else loadable { salons.nearby(here, NEARBY_RADIUS_KM) }.valueOrNull()
            state.update { it.copy(nearby = if (result == null) NearbyState.Unavailable else NearbyState.Ready(result)) }
        }
    }

    fun permissionDenied() = state.update { it.copy(nearby = NearbyState.Unavailable) }

    fun toggleFavorite(salonId: String) {
        val favorite = salonId !in uiState.value.favorites
        viewModelScope.launch { runCatching { users.toggleFavoriteSalon(salonId, favorite) } }
    }

    private companion object {
        const val PAGE_SIZE = 20L
        const val NEARBY_RADIUS_KM = 10.0
        const val STOP_TIMEOUT = 5_000L
    }
}
