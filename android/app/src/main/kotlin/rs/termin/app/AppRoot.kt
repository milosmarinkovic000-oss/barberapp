package rs.termin.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Monogram
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.auth.LoginScreen
import rs.termin.app.feature.auth.ProfileSetupScreen
import rs.termin.app.feature.onboarding.OnboardingScreen
import rs.termin.app.feature.profile.PrivacyScreen
import rs.termin.app.navigation.MainScaffold

/** Koren interfejsa: faze pre glavnog dela (uvod, prijava, profil) i sam glavni deo. */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deepLink by viewModel.deepLink.collectAsStateWithLifecycle()
    val motion = TerminTheme.motion
    val colors = TerminTheme.colors
    AnimatedContent(
        targetState = state.phase,
        contentKey = { phase -> if (phase is AppPhase.Ready) "ready-${phase.staff != null}" else phase::class.simpleName },
        transitionSpec = { fadeIn(motion.tweenSpec()) togetherWith fadeOut(motion.tweenSpec()) },
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            // Test tagovi su vidljivi UiAutomator-u (Macrobenchmark, baseline profil).
            .semantics { testTagsAsResourceId = true },
        label = "app-phase",
    ) { phase ->
        when (phase) {
            AppPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Monogram() }
            AppPhase.Onboarding -> OnboardingScreen(onFinish = viewModel::finishOnboarding)
            AppPhase.SignIn -> SignIn()
            is AppPhase.SyncFailed -> Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(TerminTheme.spacing.gutter),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorState(viewModel::retrySync, message = stringResource(phase.error.messageRes()))
                TerminButton(stringResource(R.string.profile_sign_out), viewModel::signOut, variant = ButtonVariant.Text)
            }
            AppPhase.ProfileSetup -> ProfileSetupScreen()
            is AppPhase.Ready -> MainScaffold(phase.staff, deepLink, viewModel::deepLinkHandled)
        }
    }
}

/** Prijava; politika privatnosti se otvara preko nje (dostupna i pre prijave). */
@Composable
private fun SignIn() {
    var privacy by rememberSaveable { mutableStateOf(false) }
    if (privacy) {
        BackHandler { privacy = false }
        PrivacyScreen(onBack = { privacy = false })
    } else {
        LoginScreen(onOpenPrivacy = { privacy = true })
    }
}
