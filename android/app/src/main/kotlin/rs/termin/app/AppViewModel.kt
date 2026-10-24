package rs.termin.app

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.UserRepository
import rs.termin.app.core.domain.staffSession
import java.time.Clock
import java.util.Locale
import javax.inject.Inject

/** Faza aplikacije pre glavnog ekrana. */
sealed interface AppPhase {
    /** Učitavanje (splash ostaje prikazan). */
    data object Loading : AppPhase
    data object Onboarding : AppPhase
    data object SignIn : AppPhase

    /** Prijavljen, ali nalog još nije usklađen sa serverom (npr. bez interneta pri prvoj prijavi). */
    data class SyncFailed(val error: Throwable) : AppPhase
    data object ProfileSetup : AppPhase

    /** Glavni deo aplikacije; [staff] = prikaz za vlasnika/frizera. */
    data class Ready(val uid: String, val staff: StaffSession?) : AppPhase
}

data class AppUiState(
    val phase: AppPhase = AppPhase.Loading,
    val themeConfig: ThemeConfig = ThemeConfig.Default,
    val appearance: Appearance = Appearance(themeMode = ThemeConfig.Default.initialThemeMode),
)

/** Jezik za serverska obaveštenja: izbor u aplikaciji, inače jezik telefona. */
fun notificationLanguage(): String {
    val app = AppCompatDelegate.getApplicationLocales()
    val tag = if (app.isEmpty) Locale.getDefault().language else app[0]?.language.orEmpty()
    return if (tag == "en") "en" else "sr"
}

private sealed interface SyncState {
    data object Running : SyncState
    data object Done : SyncState
    data class Failed(val error: Throwable) : SyncState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val appointments: AppointmentRepository,
    private val preferences: PreferencesRepository,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val sync = MutableStateFlow<Map<String, SyncState>>(emptyMap())
    private val pendingLink = MutableStateFlow<Uri?>(null)

    /** Deep link (obaveštenje, deljeni link salona, povratak sa OAuth-a) koji čeka glavni ekran. */
    val deepLink: StateFlow<Uri?> = pendingLink.asStateFlow()

    private val prefs = preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val profile = auth.authState
        .map { (it as? AuthState.SignedIn)?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else users.observeProfile().catch { emit(null) } }

    val uiState: StateFlow<AppUiState> = combine(auth.authState, prefs, profile, sync) { authState, p, user, syncs ->
        AppUiState(
            phase = phaseOf(authState, p, user, syncs),
            themeConfig = p?.debugThemeConfig ?: ThemeConfig.Default,
            appearance = p?.appearance ?: Appearance(themeMode = ThemeConfig.Default.initialThemeMode),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        viewModelScope.launch {
            auth.authState.map { (it as? AuthState.SignedIn)?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) onSignedIn(uid)
            }
        }
    }

    private fun phaseOf(authState: AuthState, p: AppPreferences?, user: UserProfile?, syncs: Map<String, SyncState>): AppPhase {
        if (p == null) return AppPhase.Loading
        return when (authState) {
            AuthState.Loading -> AppPhase.Loading
            AuthState.SignedOut -> if (p.onboardingDone) AppPhase.SignIn else AppPhase.Onboarding
            is AuthState.SignedIn -> when (val s = syncs[authState.uid]) {
                null, SyncState.Running -> if (user != null && user.name.isNotBlank()) ready(authState, p) else AppPhase.Loading
                is SyncState.Failed -> if (user != null && user.name.isNotBlank()) ready(authState, p) else AppPhase.SyncFailed(s.error)
                SyncState.Done -> when {
                    user == null -> AppPhase.Loading
                    user.name.isBlank() -> AppPhase.ProfileSetup
                    else -> ready(authState, p)
                }
            }
        }
    }

    private fun ready(authState: AuthState.SignedIn, p: AppPreferences): AppPhase.Ready {
        val staff = authState.staffSession()?.takeIf { !p.clientView }
        return AppPhase.Ready(authState.uid, staff)
    }

    /** Posle prijave: usklađivanje naloga (uloge), registracija za push i obnova lokalnih podsetnika. */
    private suspend fun onSignedIn(uid: String) {
        sync.update { it + (uid to SyncState.Running) }
        val result = try {
            users.syncAccount(notificationLanguage())
            SyncState.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncState.Failed(e)
        }
        sync.update { it + (uid to result) }
        if (result != SyncState.Done) return
        runCatching { users.registerPushToken() }
        rescheduleReminders(uid)
    }

    private suspend fun rescheduleReminders(uid: String) {
        val mine = runCatching { appointments.observeMine(uid).first() }.getOrNull() ?: return
        val now = clock.instant()
        mine.filter { it.start.isAfter(now) }.forEach(reminders::schedule)
    }

    fun retrySync() {
        val uid = (auth.authState.value as? AuthState.SignedIn)?.uid ?: return
        viewModelScope.launch { onSignedIn(uid) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { preferences.setOnboardingDone() }
    }

    fun signOut() {
        viewModelScope.launch {
            reminders.cancelAll()
            runCatching { auth.signOut() }
        }
    }

    fun onDeepLink(uri: Uri?) {
        if (uri != null) pendingLink.value = uri
    }

    fun deepLinkHandled() {
        pendingLink.value = null
    }
}
