package rs.termin.app.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SectionHeader
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminDialog
import rs.termin.app.core.designsystem.component.TerminExtendedFab
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.ServiceEdit
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.Stepper
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.home.icon
import rs.termin.app.feature.profile.FormScaffold
import rs.termin.app.navigation.ServiceEditRoute
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServicesViewModel @Inject constructor(auth: AuthRepository, salons: SalonRepository) : ViewModel() {
    private val retry = MutableStateFlow(0)
    val services: StateFlow<Loadable<List<Service>>> = combine(auth.authState.map { it.staffSession()?.salonId }, retry) { id, _ -> id }
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(Loadable.Loading)
            } else {
                salons.observeServices(id, includeInactive = true)
                    .map<List<Service>, Loadable<List<Service>>> { Loadable.Ready(it.sortedWith(compareBy({ s -> s.category.ordinal }, { s -> s.order }))) }
                    .onStart { emit(Loadable.Loading) }
                    .catch { emit(Loadable.Failed(it)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Loadable.Loading)

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun ServicesScreen(onBack: () -> Unit, onEdit: (serviceId: String?) -> Unit, viewModel: ServicesViewModel = hiltViewModel()) {
    val services by viewModel.services.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.services_title), onBack = onBack) },
        floatingActionButton = {
            TerminExtendedFab(stringResource(R.string.services_add), TerminIcon.Add, { onEdit(null) })
        },
    ) { padding ->
        when (val list = services) {
            Loadable.Loading -> LoadingSkeleton(Modifier.padding(padding).padding(spacing.gutter)) { SkeletonLines(6) }
            is Loadable.Failed -> ErrorState(viewModel::retry, Modifier.padding(padding))
            is Loadable.Ready -> if (list.value.isEmpty()) {
                EmptyState(Illustration.Scissors, stringResource(R.string.services_title), stringResource(R.string.services_empty), Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    list.value.groupBy { it.category }.forEach { (category, items) ->
                        item(key = "c-${category.key}", contentType = "header") { SectionHeader(f.category(category), modifier = Modifier.padding(top = spacing.sm)) }
                        items(items, key = { it.id }, contentType = { "service" }) { service ->
                            TerminCard(Modifier.padding(horizontal = spacing.gutter).fillMaxWidth().animateItem()) {
                                TerminListItem(
                                    service.name,
                                    subtitle = listOfNotNull(
                                        f.duration(service.durationMin),
                                        f.price(service.price),
                                        stringResource(R.string.services_inactive).takeIf { !service.active },
                                    ).joinToString(" · "),
                                    leadingIcon = service.category.icon(),
                                    titleColor = if (service.active) colors.text else colors.textSecondary,
                                    showChevron = true,
                                    onClick = { onEdit(service.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Izmena usluge ----------------

data class ServiceDraft(
    val category: ServiceCategory = ServiceCategory.Haircut,
    val name: String = "",
    val description: String = "",
    val durationMin: Int = DEFAULT_DURATION,
    val price: String = "",
    val stylistIds: Set<String> = emptySet(),
    val active: Boolean = true,
    val order: Int = 0,
) {
    val priceValue: Int? get() = price.filter { it.isDigit() }.toIntOrNull()
    val valid: Boolean get() = name.trim().length >= 2 && priceValue != null

    companion object {
        const val DEFAULT_DURATION = 30
        fun from(s: Service) = ServiceDraft(s.category, s.name, s.description, s.durationMin, s.price.toString(), s.stylistIds.toSet(), s.active, s.order)
    }
}

data class ServiceEditUiState(
    val salonId: String? = null,
    val isNew: Boolean = true,
    val draft: ServiceDraft? = null,
    val stylists: List<Stylist> = emptyList(),
    val saving: Boolean = false,
    val done: Boolean = false,
    val confirmDelete: Boolean = false,
    val showErrors: Boolean = false,
    val error: Throwable? = null,
)

@HiltViewModel
class ServiceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
    salons: SalonRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val serviceId = savedStateHandle.toRoute<ServiceEditRoute>().serviceId
    private val state = MutableStateFlow(ServiceEditUiState(isNew = serviceId == null))
    val uiState: StateFlow<ServiceEditUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val salonId = auth.authState.map { it.staffSession()?.salonId }.filterNotNull().first()
            runCatching {
                val services = salons.observeServices(salonId, includeInactive = true).first()
                val stylists = salons.observeStylists(salonId, includeInactive = false).first()
                val existing = services.firstOrNull { it.id == serviceId }
                // Usluga važi samo za frizere koji su izričito navedeni; nova usluga podrazumeva ceo tim.
                val draft = existing?.let(ServiceDraft::from)
                    ?: ServiceDraft(order = (services.maxOfOrNull { it.order } ?: -1) + 1, stylistIds = stylists.map { it.id }.toSet())
                state.update { it.copy(salonId = salonId, draft = draft, stylists = stylists) }
            }.onFailure { e -> state.update { it.copy(salonId = salonId, error = e) } }
        }
    }

    fun edit(change: (ServiceDraft) -> ServiceDraft) = state.update { s -> s.copy(draft = s.draft?.let(change)) }

    fun toggleStylist(id: String) = edit { d -> d.copy(stylistIds = if (id in d.stylistIds) d.stylistIds - id else d.stylistIds + id) }

    fun save() {
        val s = state.value
        val salonId = s.salonId ?: return
        val d = s.draft ?: return
        val price = d.priceValue
        if (!d.valid || price == null) {
            state.update { it.copy(showErrors = true) }
            return
        }
        launchWork {
            staff.upsertService(
                salonId,
                serviceId,
                ServiceEdit(d.category, d.name, d.description, d.durationMin, price, d.stylistIds.toList(), d.active, d.order),
            )
        }
    }

    fun requestDelete() = state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = state.update { it.copy(confirmDelete = false) }

    fun delete() {
        val salonId = state.value.salonId ?: return
        val id = serviceId ?: return
        state.update { it.copy(confirmDelete = false) }
        launchWork { staff.deleteService(salonId, id) }
    }

    private fun launchWork(block: suspend () -> Unit) {
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                state.update { it.copy(done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(saving = false) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceEditScreen(onBack: () -> Unit, viewModel: ServiceEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = formatter
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onBack() }
    FormScaffold(stringResource(if (state.isNew) R.string.service_new_title else R.string.service_edit_title), onBack) {
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        val d = state.draft
        if (d == null) {
            SkeletonLines(6)
            return@FormScaffold
        }
        Overline(stringResource(R.string.service_category))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = if (categoryExpanded) ServiceCategory.entries else ServiceCategory.entries.filter { it == d.category }
            categories.forEach { c ->
                TerminChip(f.category(c), selected = c == d.category, onClick = {
                    viewModel.edit { it.copy(category = c) }
                    categoryExpanded = !categoryExpanded
                }, icon = c.icon())
            }
        }
        TerminTextField(
            d.name,
            { v -> viewModel.edit { it.copy(name = v.take(MAX_NAME)) } },
            stringResource(R.string.service_name),
            error = if (state.showErrors && d.name.trim().length < 2) stringResource(R.string.service_error_name) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            d.description,
            { v -> viewModel.edit { it.copy(description = v.take(MAX_DESCRIPTION)) } },
            stringResource(R.string.service_description),
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        TerminCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Stepper(stringResource(R.string.service_duration), d.durationMin, { v -> viewModel.edit { it.copy(durationMin = v) } }, 5..480, 5, { f.duration(it) })
            }
        }
        TerminTextField(
            d.price,
            { v -> viewModel.edit { it.copy(price = v.filter(Char::isDigit).take(MAX_PRICE_DIGITS)) } },
            stringResource(R.string.service_price),
            leadingIcon = TerminIcon.Payments,
            error = if (state.showErrors && d.priceValue == null) stringResource(R.string.service_error_price) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.stylists.isNotEmpty()) {
            Overline(stringResource(R.string.service_stylists))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val allIds = state.stylists.map { it.id }.toSet()
                TerminChip(
                    stringResource(R.string.service_stylists_all),
                    selected = d.stylistIds.containsAll(allIds),
                    onClick = { viewModel.edit { it.copy(stylistIds = it.stylistIds + allIds) } },
                )
                state.stylists.forEach { s ->
                    TerminChip(s.name, selected = s.id in d.stylistIds, onClick = { viewModel.toggleStylist(s.id) })
                }
            }
        }
        TerminCard(Modifier.fillMaxWidth()) {
            TerminSwitchRow(stringResource(R.string.service_active), d.active, { a -> viewModel.edit { it.copy(active = a) } })
        }
        TerminButton(stringResource(R.string.action_save), viewModel::save, loading = state.saving, fullWidth = true)
        if (!state.isNew) {
            TerminButton(stringResource(R.string.service_delete), viewModel::requestDelete, variant = ButtonVariant.Danger, icon = TerminIcon.Delete, enabled = !state.saving, fullWidth = true)
        }
    }
    if (state.confirmDelete) {
        TerminDialog(
            title = stringResource(R.string.service_delete_title),
            text = stringResource(R.string.service_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete,
            destructive = true,
        )
    }
}

private const val MAX_NAME = 60
private const val MAX_DESCRIPTION = 300
private const val MAX_PRICE_DIGITS = 6
