package rs.termin.app.feature.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.WeeklyHours
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.LocationProvider
import rs.termin.app.core.domain.repository.SalonEdit
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.CityCenters
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.LocationPicker
import rs.termin.app.core.ui.Stepper
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.profile.FormScaffold
import javax.inject.Inject

data class SalonDraft(
    val name: String,
    val description: String,
    val address: String,
    val city: String,
    val phone: String,
    val location: GeoLocation,
    val priceLevel: Int,
    val hours: WeeklyHours,
    val rules: SalonRules,
    val published: Boolean,
    val photos: List<String>,
) {
    val phoneE164: String? get() = PhoneNumbers.toE164(phone)
    val valid: Boolean get() = name.trim().length >= 2 && address.trim().length >= 3 && city.isNotBlank() && phoneE164 != null && hours.isValid()

    companion object {
        fun from(salon: Salon) = SalonDraft(
            name = salon.name,
            description = salon.description,
            address = salon.address,
            city = salon.city,
            phone = PhoneNumbers.display(salon.phone),
            location = salon.location ?: CityCenters.forCity(salon.city) ?: CityCenters.Default,
            priceLevel = salon.priceLevel.coerceIn(1, 3),
            hours = salon.workingHours,
            rules = salon.rules,
            published = salon.published,
            photos = salon.photos,
        )
    }
}

data class SalonInfoUiState(
    val salonId: String? = null,
    val draft: SalonDraft? = null,
    val uploading: Int = 0,
    val locating: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val showErrors: Boolean = false,
    val error: Throwable? = null,
)

@HiltViewModel
class SalonInfoViewModel @Inject constructor(
    auth: AuthRepository,
    salons: SalonRepository,
    private val staff: StaffRepository,
    private val location: LocationProvider,
) : ViewModel() {
    private val state = MutableStateFlow(SalonInfoUiState())
    val uiState: StateFlow<SalonInfoUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val salonId = auth.authState.map { it.staffSession()?.salonId }.filterNotNull().first()
            runCatching { salons.observeSalon(salonId).filterNotNull().first() }
                .onSuccess { salon -> state.update { it.copy(salonId = salonId, draft = SalonDraft.from(salon)) } }
                .onFailure { e -> state.update { it.copy(salonId = salonId, error = e) } }
        }
    }

    fun edit(change: (SalonDraft) -> SalonDraft) = state.update { s -> s.copy(draft = s.draft?.let(change), saved = false) }

    fun useMyLocation() {
        state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val current = runCatching { location.currentLocation() }.getOrNull()
            state.update { s -> s.copy(locating = false, draft = if (current != null) s.draft?.copy(location = current) else s.draft) }
        }
    }

    fun addPhotos(uris: List<Uri>) {
        val salonId = state.value.salonId ?: return
        uris.take(MAX_PHOTOS).forEach { uri ->
            state.update { it.copy(uploading = it.uploading + 1) }
            viewModelScope.launch {
                try {
                    val path = staff.uploadSalonPhoto(salonId, uri)
                    state.update { s -> s.copy(draft = s.draft?.let { d -> d.copy(photos = (d.photos + path).take(MAX_PHOTOS)) }, saved = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.update { it.copy(error = e) }
                } finally {
                    state.update { it.copy(uploading = it.uploading - 1) }
                }
            }
        }
    }

    fun removePhoto(path: String) = edit { it.copy(photos = it.photos - path) }

    fun movePhotoFirst(path: String) = edit { it.copy(photos = listOf(path) + (it.photos - path)) }

    fun save() {
        val s = state.value
        val salonId = s.salonId ?: return
        val draft = s.draft ?: return
        if (!draft.valid) {
            state.update { it.copy(showErrors = true) }
            return
        }
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                staff.updateSalon(
                    salonId,
                    SalonEdit(
                        name = draft.name,
                        description = draft.description,
                        address = draft.address,
                        city = draft.city,
                        location = draft.location,
                        phone = draft.phoneE164 ?: draft.phone,
                        workingHours = draft.hours,
                        rules = draft.rules,
                        published = draft.published,
                        priceLevel = draft.priceLevel,
                    ),
                )
                staff.setSalonPhotos(salonId, draft.photos)
                state.update { it.copy(saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(saving = false) }
            }
        }
    }

    private companion object {
        const val MAX_PHOTOS = 12
    }
}

@Composable
fun SalonInfoScreen(onBack: () -> Unit, viewModel: SalonInfoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = formatter
    val colors = TerminTheme.colors
    val images = LocalImageUrls.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris -> viewModel.addPhotos(uris) }
    FormScaffold(stringResource(R.string.info_title), onBack) {
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        val draft = state.draft
        if (draft == null) {
            SkeletonLines(8)
            return@FormScaffold
        }
        TerminCard(Modifier.fillMaxWidth()) {
            TerminSwitchRow(
                title = stringResource(R.string.info_publish),
                subtitle = stringResource(R.string.info_publish_text),
                checked = draft.published,
                onCheckedChange = { p -> viewModel.edit { it.copy(published = p) } },
            )
        }

        Overline(stringResource(R.string.info_section_basic))
        TerminTextField(
            draft.name,
            { v -> viewModel.edit { it.copy(name = v.take(MAX_FIELD)) } },
            stringResource(R.string.create_salon_name),
            leadingIcon = TerminIcon.Storefront,
            error = if (state.showErrors && draft.name.trim().length < 2) stringResource(R.string.create_salon_error_name) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            draft.description,
            { v -> viewModel.edit { it.copy(description = v.take(MAX_DESCRIPTION)) } },
            stringResource(R.string.info_description),
            singleLine = false,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            draft.phone,
            { v -> viewModel.edit { it.copy(phone = v.take(MAX_PHONE)) } },
            stringResource(R.string.create_salon_phone),
            leadingIcon = TerminIcon.Call,
            error = if (state.showErrors && draft.phoneE164 == null) stringResource(R.string.auth_error_invalid_phone) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.info_price_level), style = TerminTheme.type.callout, color = colors.textSecondary)
        SegmentedControl(
            options = listOf(1, 2, 3),
            selected = draft.priceLevel,
            label = {
                when (it) {
                    1 -> stringResource(R.string.price_level_1)
                    2 -> stringResource(R.string.price_level_2)
                    else -> stringResource(R.string.price_level_3)
                }
            },
            onSelect = { p -> viewModel.edit { it.copy(priceLevel = p) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Overline(stringResource(R.string.info_section_location))
        TerminTextField(
            draft.address,
            { v -> viewModel.edit { it.copy(address = v.take(MAX_FIELD)) } },
            stringResource(R.string.create_salon_address),
            leadingIcon = TerminIcon.PinDrop,
            error = if (state.showErrors && draft.address.trim().length < 3) stringResource(R.string.create_salon_error_address) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            draft.city,
            { v -> viewModel.edit { it.copy(city = v.take(MAX_FIELD)) } },
            stringResource(R.string.create_salon_city),
            leadingIcon = TerminIcon.LocationOn,
            modifier = Modifier.fillMaxWidth(),
        )
        LocationPicker(
            location = draft.location,
            onLocationChange = { l -> viewModel.edit { it.copy(location = l) } },
            onUseMyLocation = viewModel::useMyLocation,
            hint = stringResource(R.string.info_location_text),
            fallbackHint = stringResource(R.string.info_location_fallback),
            locating = state.locating,
            modifier = Modifier.fillMaxWidth(),
        )

        Overline(stringResource(R.string.info_section_hours))
        HoursEditor(draft.hours, { h -> viewModel.edit { it.copy(hours = h) } })

        Overline(stringResource(R.string.info_section_rules))
        TerminCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val r = draft.rules
                Stepper(stringResource(R.string.info_cancel_hours), r.cancelHours, { v -> viewModel.edit { it.copy(rules = r.copy(cancelHours = v)) } }, 0..72, 1, { f.duration(it * 60) })
                TerminDivider()
                Stepper(stringResource(R.string.info_min_notice), r.minNoticeMinutes, { v -> viewModel.edit { it.copy(rules = r.copy(minNoticeMinutes = v)) } }, 0..1440, 15, { f.duration(it) })
                TerminDivider()
                Stepper(stringResource(R.string.info_horizon), r.horizonDays, { v -> viewModel.edit { it.copy(rules = r.copy(horizonDays = v)) } }, 1..90, 1, { it.toString() })
                TerminDivider()
                // Isti skup vrednosti koji dozvoljavaju pravila baze (firestore.rules).
                Text(stringResource(R.string.info_slot_step), style = TerminTheme.type.callout, color = colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                SegmentedControl(
                    options = SLOT_STEPS,
                    selected = r.slotStepMin.takeIf { it in SLOT_STEPS } ?: DEFAULT_SLOT_STEP,
                    label = { it.toString() },
                    onSelect = { v -> viewModel.edit { it.copy(rules = r.copy(slotStepMin = v)) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                TerminDivider()
                Stepper(stringResource(R.string.info_buffer), r.bufferMin, { v -> viewModel.edit { it.copy(rules = r.copy(bufferMin = v)) } }, 0..60, 5, { f.duration(it) })
            }
        }

        Overline(stringResource(R.string.info_section_photos))
        if (draft.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(draft.photos, key = { _, p -> p }) { index, path ->
                    Box(Modifier.width(160.dp)) {
                        TerminPhoto(images.url(path, ImageSize.Thumb), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(colors.scrim, CircleShape),
                        ) {
                            if (index > 0) TerminIconButton(TerminIcon.ArrowUpward, stringResource(R.string.info_move_first), { viewModel.movePhotoFirst(path) }, tint = Color.White)
                            TerminIconButton(TerminIcon.Delete, stringResource(R.string.info_remove_photo), { viewModel.removePhoto(path) }, tint = Color.White)
                        }
                    }
                }
            }
        }
        TerminButton(
            stringResource(R.string.info_add_photos),
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            variant = ButtonVariant.Secondary,
            icon = TerminIcon.PhotoLibrary,
            loading = state.uploading > 0,
            fullWidth = true,
        )
        if (state.saved) Notice(stringResource(R.string.info_saved), tone = NoticeTone.Success)
        TerminButton(stringResource(R.string.action_save), viewModel::save, loading = state.saving, enabled = state.uploading == 0, fullWidth = true)
    }
}

private val SLOT_STEPS = listOf(5, 10, 15, 20, 30, 60)
private const val DEFAULT_SLOT_STEP = 15
private const val MAX_FIELD = 80
private const val MAX_DESCRIPTION = 600
private const val MAX_PHONE = 20
private const val MAX_PICK = 6
