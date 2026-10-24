package rs.termin.app.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.model.AvailabilityFilter
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.model.SearchFilters
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.LocationProvider
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.loadable
import rs.termin.app.navigation.SearchRoute
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class SearchView { List, Map }

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val view: SearchView = SearchView.List,
    val results: Loadable<List<SalonListItem>> = Loadable.Loading,
    val location: GeoLocation? = null,
    val recent: List<String> = emptyList(),
    val selectedSalonId: String? = null,
    val online: Boolean = true,
    val now: Instant = Instant.EPOCH,
    val focusSearch: Boolean = false,
) {
    /** Broj aktivnih filtera (bez teksta pretrage). */
    val activeFilterCount: Int
        get() = listOfNotNull(
            filters.category,
            filters.maxPrice,
            filters.minRating,
            filters.maxDistanceKm,
            filters.availability.takeIf { it != AvailabilityFilter.Any },
        ).size
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val salons: SalonRepository,
    private val location: LocationProvider,
    private val preferences: PreferencesRepository,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SearchRoute>()
    private val state = MutableStateFlow(
        SearchUiState(
            filters = SearchFilters(
                category = route.category?.let(ServiceCategory::from),
                availability = if (route.freeToday) AvailabilityFilter.Today else AvailabilityFilter.Any,
            ),
            now = clock.instant(),
            focusSearch = route.focus,
        ),
    )
    private var searchJob: Job? = null

    val uiState: StateFlow<SearchUiState> = combine(state, preferences.preferences, connectivity.isOnline) { s, prefs, online ->
        s.copy(recent = prefs.recentSearches, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), state.value)

    init {
        viewModelScope.launch {
            if (location.hasPermission()) state.update { it.copy(location = location.currentLocation()) }
            search()
        }
        // Tekst pretrage: kratko odlaganje dok korisnik kuca.
        viewModelScope.launch {
            state.map { it.filters.query }.distinctUntilChanged().drop(1).debounce(QUERY_DEBOUNCE_MS).collect { search() }
        }
    }

    fun onQuery(query: String) = state.update { it.copy(filters = it.filters.copy(query = query.take(MAX_QUERY))) }

    fun submitQuery() {
        viewModelScope.launch { preferences.addRecentSearch(state.value.filters.query) }
        search()
    }

    fun useRecent(query: String) {
        state.update { it.copy(filters = it.filters.copy(query = query)) }
        search()
    }

    fun applyFilters(filters: SearchFilters) {
        state.update { it.copy(filters = filters.copy(query = it.filters.query)) }
        search()
    }

    fun resetFilters() = applyFilters(SearchFilters())

    fun setView(view: SearchView) = state.update { it.copy(view = view) }

    fun select(salonId: String?) = state.update { it.copy(selectedSalonId = salonId) }

    fun onLocationGranted() {
        viewModelScope.launch {
            state.update { it.copy(location = location.currentLocation()) }
            search()
        }
    }

    fun focusConsumed() = state.update { it.copy(focusSearch = false) }

    fun search() {
        searchJob?.cancel()
        state.update { it.copy(results = Loadable.Loading, now = clock.instant()) }
        searchJob = viewModelScope.launch {
            val current = state.value
            val result = loadable { salons.search(current.filters, current.location) }
            state.update { it.copy(results = result) }
        }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 300L
        const val MAX_QUERY = 60
        const val STOP_TIMEOUT = 5_000L
    }
}
