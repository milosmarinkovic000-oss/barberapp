package rs.termin.app.feature.salon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.Totals
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.UserRepository
import rs.termin.app.core.domain.totalsOf
import rs.termin.app.core.ui.Loadable
import java.time.Clock
import java.time.Instant

enum class SalonTab { Services, Stylists, Reviews, Info }

data class SalonContent(
    val salon: Salon,
    val services: List<Service>,
    val stylists: List<Stylist>,
    val reviews: List<Review>,
)

data class SalonUiState(
    val content: Loadable<SalonContent?> = Loadable.Loading,
    val tab: SalonTab = SalonTab.Services,
    val selectedServiceIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    val favoriteStylists: Set<String> = emptySet(),
    val openStylistId: String? = null,
    val now: Instant = Instant.EPOCH,
) {
    val selectedServices: List<Service>
        get() = content.valueOrNull()?.services?.filter { it.id in selectedServiceIds }.orEmpty()
    val totals: Totals get() = totalsOf(selectedServices)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SalonViewModel.Factory::class)
class SalonViewModel @AssistedInject constructor(
    @Assisted private val salonId: String,
    private val salons: SalonRepository,
    private val users: UserRepository,
    clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(salonId: String): SalonViewModel
    }

    private val local = MutableStateFlow(SalonUiState(now = clock.instant()))
    private val retry = MutableStateFlow(0)

    private val content = retry.flatMapLatest {
        combine(
            salons.observeSalon(salonId),
            salons.observeServices(salonId),
            salons.observeStylists(salonId),
            salons.observeReviews(salonId),
        ) { salon, services, stylists, reviews ->
            Loadable.Ready(salon?.let { SalonContent(it, services, stylists, reviews) }) as Loadable<SalonContent?>
        }.onStart { emit(Loadable.Loading) }.catch { emit(Loadable.Failed(it)) }
    }

    private val profile = users.observeProfile().catch { emit(null as UserProfile?) }

    val uiState: StateFlow<SalonUiState> = combine(local, content, profile) { s, c, p ->
        s.copy(
            content = c,
            favorite = p?.favoriteSalonIds?.contains(salonId) == true,
            favoriteStylists = p?.favoriteStylistKeys.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun retry() = retry.update { it + 1 }

    fun selectTab(tab: SalonTab) = local.update { it.copy(tab = tab) }

    fun toggleService(serviceId: String) = local.update {
        val ids = if (serviceId in it.selectedServiceIds) it.selectedServiceIds - serviceId else it.selectedServiceIds + serviceId
        it.copy(selectedServiceIds = ids)
    }

    fun openStylist(stylistId: String?) = local.update { it.copy(openStylistId = stylistId) }

    fun toggleFavorite() {
        val favorite = !uiState.value.favorite
        viewModelScope.launch { runCatching { users.toggleFavoriteSalon(salonId, favorite) } }
    }

    fun toggleFavoriteStylist(stylistId: String) {
        val key = "$salonId/$stylistId"
        val favorite = key !in uiState.value.favoriteStylists
        viewModelScope.launch { runCatching { users.toggleFavoriteStylist(key, favorite) } }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

/** Za mapiranje izabranih usluga u rutu zakazivanja. */
fun List<String>.toRouteArg(): String? = takeIf { it.isNotEmpty() }?.joinToString(",")
