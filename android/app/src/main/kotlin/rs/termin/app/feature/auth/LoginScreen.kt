package rs.termin.app.feature.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Monogram
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.repository.DemoRole
import rs.termin.app.core.ui.TestTags
import rs.termin.app.core.ui.messageRes

@Composable
fun LoginScreen(onOpenPrivacy: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as Activity
    LoginContent(
        state = state,
        onPhoneChange = viewModel::onPhoneChange,
        onSendCode = { viewModel.sendCode(activity) },
        onCodeChange = viewModel::onCodeChange,
        onVerify = viewModel::verify,
        onChangeNumber = viewModel::changeNumber,
        onGoogle = { viewModel.startGoogle(activity) },
        onGoogleDemo = { viewModel.googleDemo(activity, it) },
        onDismissGoogleDemo = viewModel::dismissGoogleDemo,
        onDemo = { viewModel.demo(it, activity) },
        onOpenPrivacy = onOpenPrivacy,
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onChangeNumber: () -> Unit,
    onGoogle: () -> Unit,
    onGoogleDemo: (String) -> Unit,
    onDismissGoogleDemo: () -> Unit,
    onDemo: (DemoRole) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val features = TerminTheme.features
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter, vertical = spacing.lg)
                .readableWidth(560.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Monogram(size = 64.dp)
            Text(stringResource(R.string.login_title), style = TerminTheme.type.display, color = colors.text, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.login_subtitle), style = TerminTheme.type.body, color = colors.textSecondary)

            state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }

            if (features.phoneLogin) {
                when (state.step) {
                    LoginStep.Phone -> {
                        TerminTextField(
                            value = state.phoneInput,
                            onValueChange = onPhoneChange,
                            label = stringResource(R.string.login_phone_label),
                            placeholder = stringResource(R.string.login_phone_placeholder),
                            leadingIcon = TerminIcon.Smartphone,
                            supporting = stringResource(R.string.login_phone_supporting),
                            prefix = "+381 ",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSendCode() }),
                        )
                        TerminButton(stringResource(R.string.login_send_code), onSendCode, fullWidth = true, loading = state.loading, enabled = state.phoneInput.isNotBlank())
                    }
                    LoginStep.Code -> {
                        Text(stringResource(R.string.login_code_title), style = TerminTheme.type.title2, color = colors.text)
                        Text(
                            stringResource(R.string.login_code_sent_to, PhoneNumbers.display(state.phoneE164.orEmpty())),
                            style = TerminTheme.type.body,
                            color = colors.textSecondary,
                        )
                        TerminTextField(
                            value = state.code,
                            onValueChange = onCodeChange,
                            label = stringResource(R.string.login_code_label),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onVerify() }),
                        )
                        TerminButton(stringResource(R.string.login_verify), onVerify, fullWidth = true, loading = state.loading, enabled = state.code.length == 6)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminButton(stringResource(R.string.login_change_number), onChangeNumber, variant = ButtonVariant.Text)
                            TerminButton(stringResource(R.string.login_resend), onSendCode, variant = ButtonVariant.Text, enabled = !state.loading)
                        }
                        if (state.demoAvailable) Notice(stringResource(R.string.login_emulator_hint), tone = NoticeTone.Info)
                    }
                }
            }

            if (features.googleLogin && state.googleAvailable && state.step == LoginStep.Phone) {
                if (features.phoneLogin) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TerminDivider(Modifier.weight(1f))
                        Text(stringResource(R.string.login_or), style = TerminTheme.type.callout, color = colors.textSecondary)
                        TerminDivider(Modifier.weight(1f))
                    }
                }
                TerminButton(stringResource(R.string.login_google), onGoogle, variant = ButtonVariant.Secondary, icon = TerminIcon.AccountCircle, fullWidth = true, enabled = !state.loading)
            }

            if (state.demoAvailable && state.step == LoginStep.Phone) {
                Text(stringResource(R.string.login_demo_title), style = TerminTheme.type.title3, color = colors.text, modifier = Modifier.padding(top = spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TerminButton(stringResource(R.string.login_demo_customer), { onDemo(DemoRole.Customer) }, variant = ButtonVariant.Tonal, modifier = Modifier.weight(1f).testTag(TestTags.DEMO_CUSTOMER), enabled = !state.loading)
                    TerminButton(stringResource(R.string.login_demo_owner), { onDemo(DemoRole.Owner) }, variant = ButtonVariant.Tonal, modifier = Modifier.weight(1f), enabled = !state.loading)
                    TerminButton(stringResource(R.string.login_demo_stylist), { onDemo(DemoRole.Stylist) }, variant = ButtonVariant.Tonal, modifier = Modifier.weight(1f), enabled = !state.loading)
                }
            }

            Text(stringResource(R.string.login_terms), style = TerminTheme.type.caption, color = colors.textSecondary, textAlign = TextAlign.Start)
            TerminButton(stringResource(R.string.login_privacy_link), onOpenPrivacy, variant = ButtonVariant.Text)
        }
    }

    if (state.showGoogleDemoDialog) {
        GoogleDemoDialog(onConfirm = onGoogleDemo, onDismiss = onDismissGoogleDemo)
    }
}

@Composable
private fun GoogleDemoDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    val valid = remember(email) { Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_google_demo_title), style = TerminTheme.type.title3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.login_google_demo_text), style = TerminTheme.type.body)
                TerminTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.login_google_demo_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(email) }),
                )
            }
        },
        confirmButton = { TerminButton(stringResource(R.string.action_continue), { onConfirm(email) }, enabled = valid) },
        dismissButton = { TerminButton(stringResource(R.string.action_cancel), onDismiss, variant = ButtonVariant.Text) },
        containerColor = TerminTheme.colors.surface,
    )
}
