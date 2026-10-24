package rs.termin.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.repository.AppLanguage
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.InboxRepository
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.UserRepository
import java.util.Locale
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile? = null,
    val unread: Int = 0,
    val preferences: AppPreferences = AppPreferences(),
    val signingOut: Boolean = false,
    val error: Throwable? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    inbox: InboxRepository,
    private val preferences: PreferencesRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    private val local = MutableStateFlow(ProfileUiState())

    private val unread = auth.authState.flatMapLatest { state ->
        if (state is AuthState.SignedIn) inbox.observeInbox(state.uid).map { list -> list.count { !it.read } }.catch { emit(0) } else flowOf(0)
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        local,
        users.observeProfile().catch { emit(null) },
        unread,
        preferences.preferences,
    ) { s, profile, count, prefs ->
        s.copy(profile = profile, unread = count, preferences = prefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    /** Čuva izbor jezika; sam prelaz jezika radi AppCompat u aktivnosti (vidi ekran). */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferences.setLanguage(language)
            val tag = language.tag ?: Locale.getDefault().toLanguageTag()
            runCatching { users.setLanguage(if (tag.startsWith("en")) "en" else "sr") }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { users.setNotificationsEnabled(enabled) }.onFailure { e -> local.update { it.copy(error = e) } }
        }
    }

    fun setClientView(clientView: Boolean) {
        viewModelScope.launch { preferences.setClientView(clientView) }
    }

    fun signOut() {
        if (local.value.signingOut) return
        local.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            reminders.cancelAll()
            preferences.setClientView(false)
            runCatching { auth.signOut() }
            local.update { it.copy(signingOut = false) }
        }
    }

    fun clearError() = local.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
