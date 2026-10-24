package rs.termin.app.feature.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.Loadable
import javax.inject.Inject

data class ManageData(val salon: Salon, val services: List<Service>, val stylists: List<Stylist>) {
    val missingServices: Boolean get() = services.none { it.active }
    val missingTeam: Boolean get() = stylists.none { it.active }
    val missingPhotos: Boolean get() = salon.photos.isEmpty()
    val missingHours: Boolean get() = salon.workingHours.days.values.all { it.isEmpty() }
}

data class ManageUiState(val session: StaffSession? = null, val data: Loadable<ManageData> = Loadable.Loading)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalonManageViewModel @Inject constructor(auth: AuthRepository, salons: SalonRepository) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val session = auth.authState.map { it.staffSession() }.distinctUntilChanged()

    val uiState: StateFlow<ManageUiState> = combine(session, retry) { s, _ -> s }
        .flatMapLatest { s ->
            if (s == null) {
                flowOf(ManageUiState())
            } else {
                combine(
                    salons.observeSalon(s.salonId),
                    salons.observeServices(s.salonId, includeInactive = true),
                    salons.observeStylists(s.salonId, includeInactive = true),
                ) { salon, services, stylists ->
                    val data: Loadable<ManageData> =
                        if (salon == null) Loadable.Failed(NoSuchElementException(s.salonId)) else Loadable.Ready(ManageData(salon, services, stylists))
                    ManageUiState(s, data)
                }.onStart { emit(ManageUiState(s)) }.catch { emit(ManageUiState(s, Loadable.Failed(it))) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ManageUiState())

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

data class ManageActions(
    val onInfo: () -> Unit,
    val onServices: () -> Unit,
    val onTeam: () -> Unit,
    val onStats: () -> Unit,
    val onReviews: () -> Unit,
    val onAppearance: () -> Unit,
    val onIntegrations: () -> Unit,
    val onPreview: (salonId: String) -> Unit,
    val onMyProfile: (stylistId: String) -> Unit,
    val onTimeOff: (stylistId: String) -> Unit,
)

/** Kartica „Salon“: vlasnik upravlja svime, frizer svojim profilom, odsustvima i kalendarima. */
@Composable
fun SalonManageScreen(contentPadding: PaddingValues, actions: ManageActions, viewModel: SalonManageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val features = TerminTheme.features
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .readableWidth()
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                stringResource(R.string.manage_title),
                style = TerminTheme.type.title1,
                color = colors.text,
                modifier = Modifier
                    .padding(top = spacing.lg)
                    .semantics { heading() },
            )
            val session = state.session
            when (val loaded = state.data) {
                Loadable.Loading -> LoadingSkeleton { SkeletonBox(Modifier.fillMaxWidth().height(200.dp), TerminTheme.radii.cardShape) }
                is Loadable.Failed -> ErrorState(viewModel::retry)
                is Loadable.Ready -> {
                    val data = loaded.value
                    val salon = data.salon
                    TerminCard(Modifier.fillMaxWidth(), onClick = { actions.onPreview(salon.id) }) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TerminPhoto(
                                url = LocalImageUrls.current.url(salon.coverPhoto ?: salon.photos.firstOrNull(), ImageSize.Thumb),
                                contentDescription = null,
                                placeholderLabel = salon.name,
                                modifier = Modifier.size(64.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(salon.name, style = TerminTheme.type.title3, color = colors.text)
                                Text("${salon.address}, ${salon.city}", style = TerminTheme.type.callout, color = colors.textSecondary)
                                Text(
                                    stringResource(if (salon.published) R.string.manage_published else R.string.manage_unpublished),
                                    style = TerminTheme.type.caption,
                                    color = if (salon.published) colors.accentText else colors.onWarningSoft,
                                )
                            }
                            TerminIconImage(TerminIcon.OpenInNew, contentDescription = stringResource(R.string.manage_preview), tint = colors.textSecondary)
                        }
                    }
                    if (session?.isOwner == true) {
                        if (!salon.published && (data.missingServices || data.missingTeam || data.missingHours || data.missingPhotos)) {
                            Checklist(data)
                        }
                        Overline(stringResource(R.string.manage_title))
                        TerminCard(Modifier.fillMaxWidth()) {
                            TerminListItem(stringResource(R.string.manage_info), leadingIcon = TerminIcon.Storefront, showChevron = true, onClick = actions.onInfo)
                            TerminDivider()
                            TerminListItem(
                                stringResource(R.string.manage_services),
                                subtitle = data.services.count { it.active }.toString(),
                                leadingIcon = TerminIcon.ContentCut,
                                showChevron = true,
                                onClick = actions.onServices,
                            )
                            TerminDivider()
                            TerminListItem(
                                stringResource(R.string.manage_team),
                                subtitle = data.stylists.count { it.active }.toString(),
                                leadingIcon = TerminIcon.Group,
                                showChevron = true,
                                onClick = actions.onTeam,
                            )
                            if (features.anyCalendarIntegration) {
                                TerminDivider()
                                TerminListItem(stringResource(R.string.manage_integrations), leadingIcon = TerminIcon.Sync, showChevron = true, onClick = actions.onIntegrations)
                            }
                        }
                        TerminCard(Modifier.fillMaxWidth()) {
                            if (features.ownerStats) {
                                TerminListItem(stringResource(R.string.manage_stats), leadingIcon = TerminIcon.BarChart, showChevron = true, onClick = actions.onStats)
                                TerminDivider()
                            }
                            if (features.reviews) {
                                TerminListItem(
                                    stringResource(R.string.manage_reviews),
                                    subtitle = salon.rating.count.toString(),
                                    leadingIcon = TerminIcon.Reviews,
                                    showChevron = true,
                                    onClick = actions.onReviews,
                                )
                                TerminDivider()
                            }
                            if (features.salonBranding) {
                                TerminListItem(stringResource(R.string.manage_appearance), leadingIcon = TerminIcon.Palette, showChevron = true, onClick = actions.onAppearance)
                                TerminDivider()
                            }
                            val shareText = stringResource(R.string.manage_share_text, salon.name, Actions.salonLink(salon.id))
                            val shareTitle = stringResource(R.string.manage_share)
                            TerminListItem(shareTitle, leadingIcon = TerminIcon.Share, onClick = { Actions.share(context, shareText, shareTitle) })
                        }
                    }
                    val ownStylist = session?.stylistId
                    if (ownStylist != null) {
                        Overline(stringResource(R.string.nav_me))
                        TerminCard(Modifier.fillMaxWidth()) {
                            TerminListItem(stringResource(R.string.manage_my_profile), leadingIcon = TerminIcon.Badge, showChevron = true, onClick = { actions.onMyProfile(ownStylist) })
                            TerminDivider()
                            TerminListItem(stringResource(R.string.manage_my_time_off), leadingIcon = TerminIcon.BeachAccess, showChevron = true, onClick = { actions.onTimeOff(ownStylist) })
                            if (features.anyCalendarIntegration && !session.isOwner) {
                                TerminDivider()
                                TerminListItem(stringResource(R.string.manage_my_integrations), leadingIcon = TerminIcon.Sync, showChevron = true, onClick = actions.onIntegrations)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Checklist(data: ManageData) {
    val items = buildList {
        if (data.missingServices) add(R.string.manage_check_services)
        if (data.missingTeam) add(R.string.manage_check_team)
        if (data.missingHours) add(R.string.manage_check_hours)
        if (data.missingPhotos) add(R.string.manage_check_photos)
    }
    Notice(
        stringResource(R.string.manage_checklist) + ":\n" + items.map { stringResource(it) }.joinToString("\n") { "• $it" },
        tone = NoticeTone.Warning,
    )
}
