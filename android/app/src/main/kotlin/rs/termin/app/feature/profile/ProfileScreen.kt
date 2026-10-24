package rs.termin.app.feature.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.BuildConfig
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDialog
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.repository.AppLanguage
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.messageRes
import rs.termin.app.core.domain.model.Role as UserRole

data class ProfileActions(
    val onEditProfile: () -> Unit,
    val onFavorites: () -> Unit,
    val onInbox: () -> Unit,
    val onAppearance: () -> Unit,
    val onPrivacy: () -> Unit,
    val onLicenses: () -> Unit,
    val onCreateSalon: () -> Unit,
    val onDeleteAccount: () -> Unit,
    val onStylePreview: () -> Unit,
)

@Composable
fun ProfileScreen(contentPadding: PaddingValues, actions: ProfileActions, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val features = TerminTheme.features
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var systemAllowsNotifications by rememberSaveable { mutableStateOf(true) }
    // Korisnik može da promeni dozvolu u podešavanjima telefona; proverava se pri svakom povratku.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemAllowsNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemAllowsNotifications = granted
        if (granted) viewModel.setNotificationsEnabled(true)
    }
    val profile = state.profile

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
                stringResource(R.string.profile_title),
                style = TerminTheme.type.title1,
                color = colors.text,
                modifier = Modifier
                    .padding(top = spacing.lg)
                    .semantics { heading() },
            )
            TerminCard(Modifier.fillMaxWidth(), onClick = actions.onEditProfile) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(LocalImageUrls.current.url(profile?.photoPath), profile?.name.orEmpty(), size = 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(profile?.name.orEmpty().ifBlank { stringResource(R.string.profile_edit) }, style = TerminTheme.type.title3, color = colors.text)
                        val contact = profile?.phone?.let(PhoneNumbers::display) ?: profile?.email
                        if (contact != null) Text(contact, style = TerminTheme.type.callout, color = colors.textSecondary)
                        val roles = profile?.roles.orEmpty()
                        val roleLabel = when {
                            UserRole.Owner in roles -> stringResource(R.string.profile_role_owner)
                            UserRole.Stylist in roles -> stringResource(R.string.profile_role_stylist)
                            else -> null
                        }
                        if (roleLabel != null) Text(roleLabel, style = TerminTheme.type.caption, color = colors.accentText)
                    }
                    TerminIconImage(TerminIcon.Edit, contentDescription = stringResource(R.string.profile_edit), tint = colors.textSecondary)
                }
            }
            state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }

            Group(stringResource(R.string.profile_section_mine)) {
                if (features.favorites) {
                    TerminListItem(stringResource(R.string.profile_favorites), leadingIcon = TerminIcon.Favorite, showChevron = true, onClick = actions.onFavorites)
                    TerminDivider()
                }
                TerminListItem(
                    stringResource(R.string.profile_inbox),
                    leadingIcon = TerminIcon.Inbox,
                    showChevron = true,
                    trailing = if (state.unread > 0) {
                        { UnreadBadge(state.unread) }
                    } else {
                        null
                    },
                    onClick = actions.onInbox,
                )
            }

            Group(stringResource(R.string.profile_section_settings)) {
                if (features.inAppAppearance) {
                    TerminListItem(stringResource(R.string.profile_appearance), leadingIcon = TerminIcon.Palette, showChevron = true, onClick = actions.onAppearance)
                    TerminDivider()
                }
                if (features.english) {
                    TerminListItem(
                        stringResource(R.string.profile_language),
                        subtitle = languageLabel(currentLanguage()),
                        leadingIcon = TerminIcon.Language,
                        showChevron = true,
                        onClick = { showLanguage = true },
                    )
                    TerminDivider()
                }
                TerminSwitchRow(
                    title = stringResource(R.string.profile_notifications),
                    subtitle = stringResource(R.string.profile_notifications_text),
                    checked = (profile?.notificationsEnabled ?: true) && systemAllowsNotifications,
                    onCheckedChange = { enabled ->
                        if (enabled && !systemAllowsNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    },
                )
                if (!systemAllowsNotifications && state.preferences.notificationPermissionAsked) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Notice(stringResource(R.string.profile_notifications_blocked), tone = NoticeTone.Warning)
                        TerminButton(
                            stringResource(R.string.profile_open_system_settings),
                            {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            variant = ButtonVariant.Text,
                            icon = TerminIcon.OpenInNew,
                        )
                    }
                }
            }

            Group(stringResource(R.string.profile_section_business)) {
                if (profile?.isStaff == true) {
                    TerminSwitchRow(
                        title = stringResource(R.string.profile_staff_view),
                        subtitle = stringResource(R.string.profile_staff_view_text),
                        checked = !state.preferences.clientView,
                        onCheckedChange = { staff -> viewModel.setClientView(!staff) },
                    )
                } else {
                    TerminListItem(
                        stringResource(R.string.profile_create_salon),
                        subtitle = stringResource(R.string.profile_create_salon_text),
                        leadingIcon = TerminIcon.Storefront,
                        showChevron = true,
                        onClick = actions.onCreateSalon,
                    )
                }
            }

            Group(stringResource(R.string.profile_section_about)) {
                TerminListItem(stringResource(R.string.profile_privacy), leadingIcon = TerminIcon.Policy, showChevron = true, onClick = actions.onPrivacy)
                TerminDivider()
                TerminListItem(stringResource(R.string.profile_licenses), leadingIcon = TerminIcon.Description, showChevron = true, onClick = actions.onLicenses)
                if (BuildConfig.DEBUG) {
                    TerminDivider()
                    TerminListItem(stringResource(R.string.profile_style_preview), leadingIcon = TerminIcon.AutoAwesome, showChevron = true, onClick = actions.onStylePreview)
                }
            }

            TerminButton(
                stringResource(R.string.profile_sign_out),
                { confirmSignOut = true },
                variant = ButtonVariant.Secondary,
                icon = TerminIcon.Logout,
                loading = state.signingOut,
                fullWidth = true,
            )
            TerminButton(
                stringResource(R.string.profile_delete_account),
                actions.onDeleteAccount,
                variant = ButtonVariant.Text,
                icon = TerminIcon.Delete,
                fullWidth = true,
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.profile_version, BuildConfig.VERSION_NAME), style = TerminTheme.type.caption, color = colors.textSecondary)
                if (BuildConfig.USE_EMULATORS) {
                    Text(stringResource(R.string.profile_demo_mode), style = TerminTheme.type.caption, color = colors.textSecondary)
                }
            }
        }
    }

    if (showLanguage) {
        LanguageDialog(
            current = currentLanguage(),
            onSelect = { language ->
                showLanguage = false
                viewModel.setLanguage(language)
                AppCompatDelegate.setApplicationLocales(
                    if (language.tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag),
                )
            },
            onDismiss = { showLanguage = false },
        )
    }
    if (confirmSignOut) {
        TerminDialog(
            title = stringResource(R.string.profile_sign_out_title),
            text = stringResource(R.string.profile_sign_out_text),
            confirmLabel = stringResource(R.string.profile_sign_out),
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** Jezik koji je trenutno postavljen za aplikaciju (AppCompat per-app locale). */
fun currentLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return AppLanguage.System
    val tag = locales[0]?.language.orEmpty()
    return if (tag == "en") AppLanguage.English else AppLanguage.Serbian
}

@Composable
fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.System -> stringResource(R.string.language_system)
    AppLanguage.Serbian -> stringResource(R.string.language_serbian)
    AppLanguage.English -> stringResource(R.string.language_english)
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(title, Modifier.padding(start = 4.dp))
        TerminCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val colors = TerminTheme.colors
    Text(
        if (count > 99) "99+" else count.toString(),
        style = TerminTheme.type.caption,
        color = colors.onAccent,
        modifier = Modifier
            .background(colors.accent, TerminTheme.radii.smallShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    val colors = TerminTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        TerminCard(Modifier.fillMaxWidth(), elevated = true) {
            Column(Modifier.padding(vertical = 16.dp).selectableGroup()) {
                Text(
                    stringResource(R.string.profile_language),
                    style = TerminTheme.type.title3,
                    color = colors.text,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = language == current, onClick = { onSelect(language) }, role = Role.RadioButton)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(
                            selected = language == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accentText, unselectedColor = colors.outline),
                        )
                        Text(languageLabel(language), style = TerminTheme.type.body, color = colors.text)
                    }
                }
            }
        }
    }
}
