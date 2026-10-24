package rs.termin.app.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminExtendedFab
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.messageRes
import javax.inject.Inject

data class InviteState(
    val name: String = "",
    val phone: String = "",
    val sending: Boolean = false,
    val linked: Boolean? = null,
    val showErrors: Boolean = false,
    val error: Throwable? = null,
) {
    val phoneE164: String? get() = PhoneNumbers.toE164(phone)
    val valid: Boolean get() = name.trim().length >= 2 && phoneE164 != null
}

data class TeamUiState(
    val session: StaffSession? = null,
    val stylists: Loadable<List<Stylist>> = Loadable.Loading,
    val invite: InviteState? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TeamViewModel @Inject constructor(
    auth: AuthRepository,
    salons: SalonRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val invite = MutableStateFlow<InviteState?>(null)
    private val retry = MutableStateFlow(0)
    private val session = auth.authState.map { it.staffSession() }.distinctUntilChanged()

    private val stylists = combine(session, retry) { s, _ -> s }.flatMapLatest { s ->
        if (s == null) {
            flowOf(Loadable.Loading)
        } else {
            salons.observeStylists(s.salonId, includeInactive = true)
                .map<List<Stylist>, Loadable<List<Stylist>>> { Loadable.Ready(it.sortedWith(compareBy({ st -> !st.active }, { st -> st.order }))) }
                .onStart { emit(Loadable.Loading) }
                .catch { emit(Loadable.Failed(it)) }
        }
    }

    val uiState: StateFlow<TeamUiState> = combine(session, stylists, invite) { s, list, i -> TeamUiState(s, list, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), TeamUiState())

    fun openInvite() = invite.update { InviteState() }

    fun closeInvite() = invite.update { null }

    fun onInviteName(value: String) = invite.update { it?.copy(name = value.take(MAX_NAME)) }

    fun onInvitePhone(value: String) = invite.update { it?.copy(phone = value.take(MAX_PHONE)) }

    fun sendInvite() {
        val s = invite.value ?: return
        val salonId = uiState.value.session?.salonId ?: return
        val phone = s.phoneE164
        if (!s.valid || phone == null) {
            invite.update { it?.copy(showErrors = true) }
            return
        }
        invite.update { it?.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                val linked = staff.inviteStylist(salonId, phone, s.name.trim())
                invite.update { it?.copy(linked = linked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                invite.update { it?.copy(error = e) }
            } finally {
                invite.update { it?.copy(sending = false) }
            }
        }
    }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MAX_NAME = 60
        const val MAX_PHONE = 20
    }
}

@Composable
fun TeamScreen(onBack: () -> Unit, onOpenStylist: (String) -> Unit, viewModel: TeamViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val images = LocalImageUrls.current
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.team_title), onBack = onBack) },
        floatingActionButton = {
            TerminExtendedFab(stringResource(R.string.team_invite), TerminIcon.PersonAdd, viewModel::openInvite)
        },
    ) { padding ->
        when (val list = state.stylists) {
            Loadable.Loading -> LoadingSkeleton(Modifier.padding(padding).padding(spacing.gutter)) { SkeletonLines(5) }
            is Loadable.Failed -> ErrorState(viewModel::retry, Modifier.padding(padding))
            is Loadable.Ready -> if (list.value.isEmpty()) {
                EmptyState(Illustration.Team, stringResource(R.string.team_title), stringResource(R.string.team_empty), Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.gutter,
                        end = spacing.gutter,
                        top = padding.calculateTopPadding() + spacing.sm,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(list.value, key = { it.id }) { stylist ->
                        TerminCard(Modifier.fillMaxWidth().animateItem()) {
                            TerminListItem(
                                stylist.name,
                                subtitle = when {
                                    !stylist.active -> stringResource(R.string.team_inactive)
                                    stylist.uid != null -> stringResource(R.string.team_access_on)
                                    else -> stringResource(R.string.team_access_off)
                                },
                                leading = { Avatar(images.url(stylist.photoPath, ImageSize.Thumb), stylist.name) },
                                titleColor = if (stylist.active) colors.text else colors.textSecondary,
                                showChevron = true,
                                onClick = { onOpenStylist(stylist.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    state.invite?.let { invite -> InviteSheet(invite, viewModel) }
}

@Composable
private fun InviteSheet(invite: InviteState, viewModel: TeamViewModel) {
    val spacing = TerminTheme.spacing
    TerminBottomSheet(viewModel::closeInvite) {
        Column(
            Modifier
                .imePadding()
                .padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(stringResource(R.string.team_invite), style = TerminTheme.type.title2, color = TerminTheme.colors.text, modifier = Modifier.semantics { heading() })
            val linked = invite.linked
            if (linked != null) {
                Notice(stringResource(if (linked) R.string.team_invite_linked else R.string.team_invite_pending), tone = NoticeTone.Success)
                TerminButton(stringResource(R.string.action_done), viewModel::closeInvite, fullWidth = true)
                return@Column
            }
            Text(stringResource(R.string.team_invite_text), style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
            invite.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
            TerminTextField(
                invite.name,
                viewModel::onInviteName,
                stringResource(R.string.stylist_name),
                leadingIcon = TerminIcon.Person,
                error = if (invite.showErrors && invite.name.trim().length < 2) stringResource(R.string.manual_error_name) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TerminTextField(
                invite.phone,
                viewModel::onInvitePhone,
                stringResource(R.string.login_phone_label),
                leadingIcon = TerminIcon.Call,
                error = if (invite.showErrors && invite.phoneE164 == null) stringResource(R.string.auth_error_invalid_phone) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            TerminButton(stringResource(R.string.team_invite_send), viewModel::sendInvite, icon = TerminIcon.PersonAdd, loading = invite.sending, fullWidth = true)
        }
    }
}
