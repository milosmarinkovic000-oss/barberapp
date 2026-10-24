package rs.termin.app.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminDialog
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.IntegrationState
import rs.termin.app.core.domain.model.Provider
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.IntegrationRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.profile.FormScaffold
import rs.termin.app.navigation.IntegrationsRoute
import javax.inject.Inject

/** Ishod povratka sa OAuth stranice (deep link termin://integrations?provider=&status=&reason=). */
data class OAuthResult(val provider: Provider, val ok: Boolean, val reason: String)

data class IntegrationsUiState(
    val session: StaffSession? = null,
    val stylists: List<Stylist>? = null,
    val selectedId: String? = null,
    val configured: Map<Provider, Boolean>? = null,
    val busy: Provider? = null,
    val result: OAuthResult? = null,
    val openUrl: String? = null,
    val confirmDisconnect: Provider? = null,
    val error: Throwable? = null,
) {
    val selected: Stylist? get() = stylists?.firstOrNull { it.id == selectedId } ?: stylists?.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IntegrationsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    salons: SalonRepository,
    private val integrations: IntegrationRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<IntegrationsRoute>()
    private val local = MutableStateFlow(
        IntegrationsUiState(
            result = route.provider?.let(Provider::from)?.let { p -> OAuthResult(p, route.status == "ok", route.reason.orEmpty()) },
        ),
    )
    private val session = auth.authState.map { it.staffSession() }.distinctUntilChanged()

    private val stylists = session.flatMapLatest { s ->
        if (s == null) {
            flowOf(null)
        } else {
            salons.observeStylists(s.salonId).map { list -> list.filter { it.active && s.canManageStylist(it.id) } }.catch { emit(emptyList()) }
        }
    }

    val uiState: StateFlow<IntegrationsUiState> = combine(local, session, stylists) { l, s, list ->
        l.copy(session = s, stylists = list, selectedId = l.selectedId ?: s?.stylistId?.takeIf { id -> list?.any { it.id == id } == true })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    init {
        viewModelScope.launch {
            val status = runCatching { integrations.serverStatus() }
            local.update { it.copy(configured = status.getOrDefault(Provider.entries.associateWith { false }), error = status.exceptionOrNull()) }
        }
    }

    fun select(id: String) = local.update { it.copy(selectedId = id) }

    private fun work(provider: Provider, block: suspend (salonId: String, stylistId: String) -> Unit) {
        val state = uiState.value
        val salonId = state.session?.salonId ?: return
        val stylistId = state.selected?.id ?: return
        local.update { it.copy(busy = provider, error = null, result = null) }
        viewModelScope.launch {
            try {
                block(salonId, stylistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e) }
            } finally {
                local.update { it.copy(busy = null) }
            }
        }
    }

    fun connect(provider: Provider) = work(provider) { salonId, stylistId ->
        val url = integrations.startOAuth(provider, salonId, stylistId)
        local.update { it.copy(openUrl = url) }
    }

    fun urlOpened() = local.update { it.copy(openUrl = null) }

    fun setEnabled(provider: Provider, enabled: Boolean) = work(provider) { salonId, stylistId ->
        integrations.setEnabled(provider, salonId, stylistId, enabled)
    }

    fun requestDisconnect(provider: Provider) = local.update { it.copy(confirmDisconnect = provider) }

    fun dismissDisconnect() = local.update { it.copy(confirmDisconnect = null) }

    fun disconnect() {
        val provider = local.value.confirmDisconnect ?: return
        local.update { it.copy(confirmDisconnect = null) }
        work(provider) { salonId, stylistId -> integrations.disconnect(provider, salonId, stylistId) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
private fun providerTitle(provider: Provider): String = stringResource(
    when (provider) {
        Provider.Google -> R.string.integrations_google
        Provider.Calendly -> R.string.integrations_calendly
        Provider.Calcom -> R.string.integrations_calcom
    },
)

@Composable
private fun providerText(provider: Provider): String = stringResource(
    when (provider) {
        Provider.Google -> R.string.integrations_google_text
        Provider.Calendly -> R.string.integrations_calendly_text
        Provider.Calcom -> R.string.integrations_calcom_text
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntegrationsScreen(onBack: () -> Unit, viewModel: IntegrationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val features = TerminTheme.features
    var shownResult by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(state.openUrl) {
        state.openUrl?.let { url ->
            Actions.openCustomTab(context, url)
            viewModel.urlOpened()
        }
    }
    val providers = Provider.entries.filter {
        when (it) {
            Provider.Google -> features.googleCalendar
            Provider.Calendly -> features.calendly
            Provider.Calcom -> features.calcom
        }
    }
    FormScaffold(stringResource(R.string.integrations_title), onBack) {
        Text(stringResource(R.string.integrations_intro), style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
        val result = state.result
        if (result != null && shownResult) {
            Notice(
                if (result.ok) stringResource(R.string.integrations_result_ok) else stringResource(R.string.integrations_result_error, result.reason.ifBlank { providerTitle(result.provider) }),
                tone = if (result.ok) NoticeTone.Success else NoticeTone.Error,
            )
        }
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        val stylists = state.stylists
        val configured = state.configured
        if (stylists == null || configured == null) {
            SkeletonLines(5)
            return@FormScaffold
        }
        if (stylists.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stylists.forEach { s ->
                    TerminChip(s.name, selected = s.id == state.selected?.id, onClick = {
                        shownResult = false
                        viewModel.select(s.id)
                    })
                }
            }
        }
        val stylist = state.selected ?: return@FormScaffold
        Overline(stringResource(R.string.integrations_for, stylist.name))
        providers.forEach { provider ->
            ProviderCard(
                provider = provider,
                integration = stylist.integrations[provider],
                configured = configured[provider] == true,
                busy = state.busy == provider,
                onConnect = { viewModel.connect(provider) },
                onEnabled = { viewModel.setEnabled(provider, it) },
                onDisconnect = { viewModel.requestDisconnect(provider) },
            )
        }
    }
    state.confirmDisconnect?.let {
        TerminDialog(
            title = stringResource(R.string.integrations_disconnect_title),
            text = stringResource(R.string.integrations_disconnect_text),
            confirmLabel = stringResource(R.string.integrations_disconnect),
            onConfirm = viewModel::disconnect,
            onDismiss = viewModel::dismissDisconnect,
            destructive = true,
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    integration: IntegrationState?,
    configured: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    val colors = TerminTheme.colors
    val connected = integration?.connected == true
    TerminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TerminIconImage(if (connected) TerminIcon.CheckCircle else TerminIcon.CalendarMonth, contentDescription = null, tint = if (connected) colors.success else colors.textSecondary)
                Column(Modifier.weight(1f)) {
                    Text(providerTitle(provider), style = TerminTheme.type.title3, color = colors.text)
                    Text(
                        if (connected) stringResource(R.string.integrations_connected, integration.accountLabel) else stringResource(R.string.integrations_not_connected),
                        style = TerminTheme.type.callout,
                        color = if (connected) colors.accentText else colors.textSecondary,
                    )
                }
            }
            Text(providerText(provider), style = TerminTheme.type.callout, color = colors.textSecondary)
            when {
                connected -> {
                    TerminSwitchRow(stringResource(R.string.integrations_sync), integration.enabled, onEnabled, enabled = !busy)
                    TerminButton(stringResource(R.string.integrations_disconnect), onDisconnect, variant = ButtonVariant.Text, icon = TerminIcon.Link, enabled = !busy)
                }
                configured -> TerminButton(stringResource(R.string.integrations_connect), onConnect, icon = TerminIcon.Link, loading = busy, fullWidth = true)
                else -> Notice(stringResource(R.string.integrations_not_configured), icon = TerminIcon.Info)
            }
        }
    }
}
