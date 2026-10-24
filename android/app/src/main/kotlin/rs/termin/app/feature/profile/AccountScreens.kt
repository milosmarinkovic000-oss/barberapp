package rs.termin.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.LocationPicker
import rs.termin.app.core.ui.messageRes

/** Zajednički okvir formi: traka sa naslovom, skrol, prostor za tastaturu i čitljiva širina. */
@Composable
fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = TerminTheme.colors.background,
        topBar = { TerminTopBar(title, onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .readableWidth()
                    .fillMaxWidth()
                    .padding(TerminTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(TerminTheme.spacing.md),
            ) { content() }
        }
    }
}

@Composable
fun EditProfileScreen(onBack: () -> Unit, viewModel: EditProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.onPhoto(uri) }
    val profile = state.profile
    FormScaffold(stringResource(R.string.edit_profile_title), onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(LocalImageUrls.current.url(profile?.photoPath), state.name, size = 96.dp)
            TerminButton(
                stringResource(R.string.edit_profile_photo),
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                variant = ButtonVariant.Text,
                icon = TerminIcon.PhotoCamera,
                loading = state.uploading,
            )
        }
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        TerminTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = stringResource(R.string.edit_profile_name),
            leadingIcon = TerminIcon.Person,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        // Telefon i e-pošta dolaze sa naloga za prijavu i ne menjaju se ovde.
        profile?.phone?.let {
            TerminListItem(PhoneNumbers.display(it), subtitle = stringResource(R.string.edit_profile_phone), leadingIcon = TerminIcon.Call)
        }
        profile?.email?.let {
            TerminListItem(it, subtitle = stringResource(R.string.edit_profile_email), leadingIcon = TerminIcon.Mail)
        }
        if (state.saved) Notice(stringResource(R.string.edit_profile_saved), tone = NoticeTone.Success)
        TerminButton(stringResource(R.string.action_save), viewModel::save, enabled = state.canSave, loading = state.saving, fullWidth = true)
    }
}

@Composable
fun DeleteAccountScreen(onBack: () -> Unit, viewModel: DeleteAccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val word = stringResource(R.string.delete_confirm_word)
    FormScaffold(stringResource(R.string.delete_title), onBack) {
        Text(stringResource(R.string.delete_text), style = TerminTheme.type.body, color = TerminTheme.colors.text)
        if (Role.Owner in state.profile?.roles.orEmpty()) {
            Notice(stringResource(R.string.delete_owner_text), tone = NoticeTone.Warning)
        }
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        TerminTextField(
            value = state.confirmation,
            onValueChange = viewModel::onConfirmation,
            label = stringResource(R.string.delete_confirm_label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminButton(
            stringResource(R.string.delete_button),
            { viewModel.delete(word) },
            variant = ButtonVariant.Danger,
            icon = TerminIcon.Delete,
            enabled = state.confirmation.trim().equals(word, ignoreCase = true),
            loading = state.deleting,
            fullWidth = true,
        )
    }
}

@Composable
fun CreateSalonScreen(onBack: () -> Unit, onCreated: (salonId: String) -> Unit, viewModel: CreateSalonViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdSalonId) { state.createdSalonId?.let(onCreated) }
    FormScaffold(stringResource(R.string.create_salon_title), onBack) {
        Text(stringResource(R.string.create_salon_intro), style = TerminTheme.type.body, color = TerminTheme.colors.textSecondary)
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        TerminTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = stringResource(R.string.create_salon_name),
            leadingIcon = TerminIcon.Storefront,
            error = if (state.showErrors && !state.nameValid) stringResource(R.string.create_salon_error_name) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            value = state.city,
            onValueChange = viewModel::onCity,
            label = stringResource(R.string.create_salon_city),
            leadingIcon = TerminIcon.LocationOn,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            value = state.address,
            onValueChange = viewModel::onAddress,
            label = stringResource(R.string.create_salon_address),
            leadingIcon = TerminIcon.PinDrop,
            error = if (state.showErrors && !state.addressValid) stringResource(R.string.create_salon_error_address) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        TerminTextField(
            value = state.phone,
            onValueChange = viewModel::onPhone,
            label = stringResource(R.string.create_salon_phone),
            leadingIcon = TerminIcon.Call,
            error = if (state.showErrors && state.phoneE164 == null) stringResource(R.string.auth_error_invalid_phone) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Overline(stringResource(R.string.create_salon_location))
        LocationPicker(
            location = state.location,
            onLocationChange = viewModel::onLocation,
            onUseMyLocation = viewModel::useMyLocation,
            hint = stringResource(R.string.create_salon_location_text),
            fallbackHint = stringResource(R.string.create_salon_location_fallback),
            locating = state.locating,
            modifier = Modifier.fillMaxWidth(),
        )
        TerminButton(stringResource(R.string.create_salon_submit), viewModel::submit, icon = TerminIcon.Check, loading = state.submitting, fullWidth = true)
    }
}
