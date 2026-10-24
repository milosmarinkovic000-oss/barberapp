package rs.termin.app.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.BuildConfig
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.model.AuthError
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.DemoRole
import rs.termin.app.core.domain.repository.PhoneCodeEvent
import javax.inject.Inject

enum class LoginStep { Phone, Code }

data class LoginUiState(
    val step: LoginStep = LoginStep.Phone,
    val phoneInput: String = "",
    val phoneE164: String? = null,
    val code: String = "",
    val verificationId: String? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
    val showGoogleDemoDialog: Boolean = false,
    val googleAvailable: Boolean = false,
    val demoAvailable: Boolean = BuildConfig.USE_EMULATORS,
)

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val state = MutableStateFlow(LoginUiState(googleAvailable = auth.isGoogleSignInAvailable))
    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

    fun onPhoneChange(value: String) = state.update { it.copy(phoneInput = value.take(20), error = null) }

    fun onCodeChange(value: String) = state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun sendCode(activity: Activity) {
        val e164 = PhoneNumbers.toE164(state.value.phoneInput)
        if (e164 == null) {
            state.update { it.copy(error = AuthError.InvalidPhone) }
            return
        }
        run {
            when (val event = auth.sendCode(e164, activity)) {
                is PhoneCodeEvent.CodeSent -> state.update {
                    it.copy(step = LoginStep.Code, phoneE164 = e164, verificationId = event.verificationId, code = "")
                }
                PhoneCodeEvent.AutoVerified -> Unit
            }
        }
    }

    fun verify() {
        val id = state.value.verificationId ?: return
        if (state.value.code.length != 6) {
            state.update { it.copy(error = AuthError.InvalidCode) }
            return
        }
        run { auth.verifyCode(id, state.value.code) }
    }

    fun changeNumber() = state.update { it.copy(step = LoginStep.Phone, code = "", verificationId = null, error = null) }

    /** U demo režimu (bez pravog Google OAuth klijenta) prvo pitamo za e-poštu probnog naloga. */
    fun startGoogle(activity: Activity) {
        if (BuildConfig.USE_EMULATORS && BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            state.update { it.copy(showGoogleDemoDialog = true) }
        } else {
            run { auth.signInWithGoogle(activity) }
        }
    }

    fun dismissGoogleDemo() = state.update { it.copy(showGoogleDemoDialog = false) }

    fun googleDemo(activity: Activity, email: String) {
        state.update { it.copy(showGoogleDemoDialog = false) }
        run { auth.signInWithGoogle(activity, email) }
    }

    fun demo(role: DemoRole, activity: Activity) = run { auth.demoSignIn(role, activity) }

    fun clearError() = state.update { it.copy(error = null) }

    private fun run(block: suspend () -> Unit) {
        if (state.value.loading) return
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(loading = false) }
            }
        }
    }
}
