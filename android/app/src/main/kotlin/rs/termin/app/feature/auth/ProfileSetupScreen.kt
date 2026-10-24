package rs.termin.app.feature.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.repository.UserRepository
import rs.termin.app.core.ui.messageRes
import javax.inject.Inject

data class ProfileSetupState(
    val name: String = "",
    val photo: Uri? = null,
    val saving: Boolean = false,
    val nameError: Boolean = false,
    val error: Throwable? = null,
)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    private val state = MutableStateFlow(ProfileSetupState())
    val uiState: StateFlow<ProfileSetupState> = state.asStateFlow()

    fun onName(value: String) = state.update { it.copy(name = value.take(60), nameError = false) }

    fun onPhoto(uri: Uri?) = state.update { it.copy(photo = uri) }

    /** Čuva ime (i fotografiju); aplikacija prelazi dalje čim profil dobije ime. */
    fun save() {
        val name = state.value.name.trim()
        if (name.length < 2) {
            state.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            state.update { it.copy(saving = true, error = null) }
            try {
                users.updateName(name)
                state.value.photo?.let { users.updatePhoto(it) }
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

/** Kratko podešavanje profila posle prve prijave: ime i (po želji) fotografija. */
@Composable
fun ProfileSetupScreen(viewModel: ProfileSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onPhoto(it) }
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.lg)
            .readableWidth(560.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(stringResource(R.string.setup_title), style = TerminTheme.type.display, color = colors.text, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.setup_subtitle), style = TerminTheme.type.body, color = colors.textSecondary)
        Avatar(url = state.photo?.toString(), name = state.name.ifBlank { "?" }, size = 88.dp)
        TerminButton(
            text = stringResource(if (state.photo == null) R.string.setup_photo else R.string.setup_photo_change),
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            variant = ButtonVariant.Secondary,
            icon = TerminIcon.PhotoCamera,
        )
        TerminTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = stringResource(R.string.setup_name_label),
            error = if (state.nameError) stringResource(R.string.setup_name_error) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
        )
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        TerminButton(stringResource(R.string.setup_finish), { viewModel.save() }, fullWidth = true, loading = state.saving)
    }
}
