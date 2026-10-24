package rs.termin.app.feature.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.domain.model.WeeklyHours
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.repository.StylistEdit
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.profile.FormScaffold
import java.time.LocalTime

data class StylistDraft(
    val name: String,
    val bio: String,
    val specialties: String,
    val ownHours: Boolean,
    val hours: WeeklyHours,
    val breaks: WeeklyHours,
    val active: Boolean,
    val portfolio: List<String>,
) {
    val specialtyList: List<String> get() = specialties.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_SPECIALTIES)
    val valid: Boolean get() = name.trim().length >= 2 && (!ownHours || hours.isValid()) && breaks.isValid()

    companion object {
        const val MAX_SPECIALTIES = 8
        fun from(s: Stylist, salonHours: WeeklyHours) = StylistDraft(
            name = s.name,
            bio = s.bio,
            specialties = s.specialties.joinToString(", "),
            ownHours = s.workingHours != null,
            hours = s.workingHours ?: salonHours,
            breaks = s.breaks,
            active = s.active,
            portfolio = s.portfolio,
        )
    }
}

data class StylistEditUiState(
    val session: StaffSession? = null,
    val stylist: Stylist? = null,
    val draft: StylistDraft? = null,
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val accessChanging: Boolean = false,
    val saved: Boolean = false,
    val error: Throwable? = null,
) {
    val canEditAll: Boolean get() = session?.isOwner == true
}

@HiltViewModel(assistedFactory = StylistEditViewModel.Factory::class)
class StylistEditViewModel @AssistedInject constructor(
    @Assisted private val stylistId: String,
    auth: AuthRepository,
    private val salons: SalonRepository,
    private val staff: StaffRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(stylistId: String): StylistEditViewModel
    }

    private val state = MutableStateFlow(StylistEditUiState())
    val uiState: StateFlow<StylistEditUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = auth.authState.map { it.staffSession() }.filterNotNull().first()
            runCatching {
                val salon = salons.observeSalon(session.salonId).filterNotNull().first()
                val stylist = salons.observeStylists(session.salonId, includeInactive = true).first().first { it.id == stylistId }
                state.update { it.copy(session = session, stylist = stylist, draft = StylistDraft.from(stylist, salon.workingHours)) }
            }.onFailure { e -> state.update { it.copy(session = session, error = e) } }
            // Prati promene (npr. fotografija, pristup) bez gubitka izmena u formi.
            salons.observeStylists(session.salonId, includeInactive = true).collect { list ->
                list.firstOrNull { it.id == stylistId }?.let { s -> state.update { it.copy(stylist = s) } }
            }
        }
    }

    fun edit(change: (StylistDraft) -> StylistDraft) = state.update { s -> s.copy(draft = s.draft?.let(change), saved = false) }

    private fun work(onDone: (StylistEditUiState) -> StylistEditUiState = { it }, block: suspend (salonId: String) -> Unit) {
        val salonId = state.value.session?.salonId ?: return
        viewModelScope.launch {
            try {
                block(salonId)
                state.update(onDone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(uploading = false, saving = false, accessChanging = false) }
            }
        }
    }

    fun uploadPortrait(uri: Uri) {
        state.update { it.copy(uploading = true, error = null) }
        work { salonId -> staff.uploadStylistPhoto(salonId, stylistId, uri, portfolio = false) }
    }

    fun addPortfolio(uris: List<Uri>) {
        if (uris.isEmpty()) return
        state.update { it.copy(uploading = true, error = null) }
        work { salonId ->
            val paths = uris.take(MAX_PORTFOLIO_PICK).map { staff.uploadStylistPhoto(salonId, stylistId, it, portfolio = true) }
            val updated = ((state.value.draft?.portfolio ?: emptyList()) + paths).take(MAX_PORTFOLIO)
            staff.setStylistPortfolio(salonId, stylistId, updated)
            state.update { s -> s.copy(draft = s.draft?.copy(portfolio = updated)) }
        }
    }

    fun removePortfolio(path: String) {
        val updated = state.value.draft?.portfolio.orEmpty() - path
        state.update { s -> s.copy(draft = s.draft?.copy(portfolio = updated), error = null) }
        work { salonId -> staff.setStylistPortfolio(salonId, stylistId, updated) }
    }

    fun setAccess(enabled: Boolean) {
        state.update { it.copy(accessChanging = true, error = null) }
        work { salonId -> staff.setStylistAccess(salonId, stylistId, enabled) }
    }

    fun save() {
        val s = state.value
        val draft = s.draft ?: return
        if (!draft.valid) return
        state.update { it.copy(saving = true, error = null) }
        work(onDone = { it.copy(saved = true) }) { salonId ->
            if (s.canEditAll) {
                staff.updateStylist(
                    salonId,
                    stylistId,
                    StylistEdit(
                        name = draft.name,
                        bio = draft.bio,
                        specialties = draft.specialtyList,
                        workingHours = if (draft.ownHours) draft.hours else null,
                        breaks = draft.breaks,
                        active = draft.active,
                    ),
                )
            } else {
                staff.updateStylistProfile(salonId, stylistId, draft.bio, draft.specialtyList)
            }
        }
    }

    private companion object {
        const val MAX_PORTFOLIO = 12
        const val MAX_PORTFOLIO_PICK = 6
    }
}

private val DefaultBreak = TimeRange(LocalTime.of(13, 0), LocalTime.of(13, 30))

@Composable
fun StylistEditScreen(
    stylistId: String,
    onBack: () -> Unit,
    onTimeOff: (String) -> Unit,
    onIntegrations: () -> Unit,
) {
    val viewModel = hiltViewModel<StylistEditViewModel, StylistEditViewModel.Factory>(key = "stylist-$stylistId") { it.create(stylistId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val images = LocalImageUrls.current
    val colors = TerminTheme.colors
    val features = TerminTheme.features
    val portrait = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::uploadPortrait) }
    val portfolio = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris -> viewModel.addPortfolio(uris) }
    FormScaffold(stringResource(R.string.stylist_edit_title), onBack) {
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        val draft = state.draft
        val stylist = state.stylist
        if (draft == null || stylist == null) {
            SkeletonLines(6)
            return@FormScaffold
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(images.url(stylist.photoPath), stylist.name, size = 96.dp)
            TerminButton(
                stringResource(R.string.stylist_photo),
                { portrait.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                variant = ButtonVariant.Text,
                icon = TerminIcon.PhotoCamera,
                loading = state.uploading,
            )
        }
        if (state.canEditAll) {
            TerminTextField(
                draft.name,
                { v -> viewModel.edit { it.copy(name = v.take(MAX_NAME)) } },
                stringResource(R.string.stylist_name),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TerminTextField(
            draft.bio,
            { v -> viewModel.edit { it.copy(bio = v.take(MAX_BIO)) } },
            stringResource(R.string.stylist_bio),
            singleLine = false,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            draft.specialties,
            { v -> viewModel.edit { it.copy(specialties = v.take(MAX_SPECIALTY_TEXT)) } },
            stringResource(R.string.stylist_edit_specialties),
            modifier = Modifier.fillMaxWidth(),
        )

        Overline(stringResource(R.string.stylist_edit_portfolio))
        if (draft.portfolio.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(draft.portfolio, key = { it }) { path ->
                    Box(Modifier.width(120.dp)) {
                        TerminPhoto(images.url(path, ImageSize.Thumb), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(colors.scrim, CircleShape),
                        ) {
                            TerminIconButton(TerminIcon.Delete, stringResource(R.string.info_remove_photo), { viewModel.removePortfolio(path) }, tint = Color.White)
                        }
                    }
                }
            }
        }
        TerminButton(
            stringResource(R.string.stylist_add_portfolio),
            { portfolio.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            variant = ButtonVariant.Secondary,
            icon = TerminIcon.PhotoLibrary,
            loading = state.uploading,
            fullWidth = true,
        )

        if (state.canEditAll) {
            TerminCard(Modifier.fillMaxWidth()) {
                TerminSwitchRow(stringResource(R.string.stylist_active), draft.active, { a -> viewModel.edit { it.copy(active = a) } })
                if (stylist.uid != null || !stylist.active) {
                    TerminDivider()
                    TerminSwitchRow(
                        title = stringResource(R.string.stylist_access),
                        subtitle = stringResource(R.string.stylist_access_text),
                        checked = stylist.uid != null && stylist.active,
                        onCheckedChange = viewModel::setAccess,
                        enabled = !state.accessChanging,
                    )
                }
                TerminDivider()
                TerminSwitchRow(
                    title = stringResource(R.string.stylist_own_hours),
                    subtitle = stringResource(R.string.stylist_own_hours_text),
                    checked = draft.ownHours,
                    onCheckedChange = { own -> viewModel.edit { it.copy(ownHours = own) } },
                )
            }
            if (draft.ownHours) {
                Overline(stringResource(R.string.info_section_hours))
                HoursEditor(draft.hours, { h -> viewModel.edit { it.copy(hours = h) } })
            }
            Overline(stringResource(R.string.stylist_breaks))
            HoursEditor(draft.breaks, { b -> viewModel.edit { it.copy(breaks = b) } }, newRange = DefaultBreak)
        }

        TerminCard(Modifier.fillMaxWidth()) {
            TerminListItem(stringResource(R.string.stylist_time_off), leadingIcon = TerminIcon.BeachAccess, showChevron = true, onClick = { onTimeOff(stylistId) })
            if (features.anyCalendarIntegration) {
                TerminDivider()
                TerminListItem(stringResource(R.string.stylist_integrations), leadingIcon = TerminIcon.Sync, showChevron = true, onClick = onIntegrations)
            }
        }
        if (state.saved) Notice(stringResource(R.string.stylist_saved), tone = NoticeTone.Success)
        TerminButton(stringResource(R.string.action_save), viewModel::save, enabled = draft.valid, loading = state.saving, fullWidth = true)
    }
}

private const val MAX_NAME = 60
private const val MAX_BIO = 500
private const val MAX_SPECIALTY_TEXT = 200
