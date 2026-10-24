package rs.termin.app.core.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rs.termin.app.BuildConfig
import rs.termin.app.core.common.ApplicationScope
import rs.termin.app.core.common.IoDispatcher
import rs.termin.app.core.data.firebase.EmulatorPorts
import rs.termin.app.core.data.firebase.parseRoles
import rs.termin.app.core.domain.model.AuthError
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.DemoRole
import rs.termin.app.core.domain.repository.PhoneCodeEvent
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Demo nalozi iz seed skripte (README). */
object DemoAccounts {
    const val CUSTOMER = "+381600000001"
    const val OWNER = "+381600000002"
    const val STYLIST = "+381600000003"

    fun phone(role: DemoRole): String = when (role) {
        DemoRole.Customer -> CUSTOMER
        DemoRole.Owner -> OWNER
        DemoRole.Stylist -> STYLIST
    }
}

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AuthRepository {

    private val state = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = state

    override val isGoogleSignInAvailable: Boolean
        get() = BuildConfig.USE_EMULATORS || BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    init {
        auth.addIdTokenListener(FirebaseAuth.IdTokenListener { firebaseAuth ->
            scope.launch { publish(firebaseAuth.currentUser, forceRefresh = false) }
        })
    }

    private suspend fun publish(user: FirebaseUser?, forceRefresh: Boolean) {
        if (user == null) {
            state.value = AuthState.SignedOut
            return
        }
        val claims = runCatching { user.getIdToken(forceRefresh).await().claims }.getOrDefault(emptyMap())
        val roles = (claims["roles"] as? List<*>).orEmpty().filterIsInstance<String>()
        state.value = AuthState.SignedIn(
            uid = user.uid,
            roles = parseRoles(roles),
            salonId = claims["salonId"] as? String,
            stylistId = claims["stylistId"] as? String,
        )
    }

    override suspend fun sendCode(phoneE164: String, activity: Activity): PhoneCodeEvent =
        suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Automatska potvrda (SMS Retriever): prijava bez kucanja koda.
                    scope.launch {
                        runCatching { auth.signInWithCredential(credential).await() }
                        if (cont.isActive) cont.resume(PhoneCodeEvent.AutoVerified)
                    }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    if (cont.isActive) cont.resumeWithException(mapError(e))
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (cont.isActive) cont.resume(PhoneCodeEvent.CodeSent(verificationId))
                }
            }
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(CODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    override suspend fun verifyCode(verificationId: String, code: String) {
        try {
            auth.signInWithCredential(PhoneAuthProvider.getCredential(verificationId, code)).await()
        } catch (e: Exception) {
            throw mapError(e)
        }
    }

    override suspend fun signInWithGoogle(activity: Activity, emulatorEmail: String?) {
        val idToken = if (BuildConfig.USE_EMULATORS && BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            // Auth emulator prihvata nepotpisan JSON kao Google ID token (demo režim).
            val email = emulatorEmail?.trim().orEmpty()
            if (email.isEmpty()) throw AuthError.Cancelled
            JSONObject()
                .put("sub", "google-${email.lowercase().hashCode().toUInt()}")
                .put("email", email)
                .put("email_verified", true)
                .put("name", email.substringBefore('@'))
                .toString()
        } else {
            googleIdToken(activity)
        }
        try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: Exception) {
            throw mapError(e)
        }
    }

    private suspend fun googleIdToken(activity: Activity): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw AuthError.Cancelled
        } catch (e: NoCredentialException) {
            // Na telefonu nema Google naloga: korisnik može da se prijavi brojem telefona.
            throw AuthError.GoogleUnavailable
        } catch (e: GetCredentialException) {
            throw AuthError.GoogleUnavailable
        }
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw AuthError.GoogleUnavailable
    }

    override suspend fun demoSignIn(role: DemoRole, activity: Activity) {
        check(BuildConfig.USE_EMULATORS) { "Demo prijava radi samo sa emulatorima" }
        val phone = DemoAccounts.phone(role)
        when (val event = sendCode(phone, activity)) {
            PhoneCodeEvent.AutoVerified -> Unit
            is PhoneCodeEvent.CodeSent -> verifyCode(event.verificationId, emulatorCode(event.verificationId, phone))
        }
    }

    /** Čita poslednji SMS kod iz Auth emulatora (isto što prikazuje Emulator UI). */
    private suspend fun emulatorCode(sessionInfo: String, phone: String): String = withContext(io) {
        val projectId = FirebaseApp.getInstance().options.projectId
        val url = URL("http://${BuildConfig.EMULATOR_HOST}:${EmulatorPorts.AUTH}/emulator/v1/projects/$projectId/verificationCodes")
        val connection = url.openConnection() as HttpURLConnection
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val codes = JSONObject(body).getJSONArray("verificationCodes")
            var code: String? = null
            for (i in codes.length() - 1 downTo 0) {
                val item = codes.getJSONObject(i)
                if (item.optString("sessionInfo") == sessionInfo || (code == null && item.optString("phoneNumber") == phone)) {
                    code = item.getString("code")
                    if (item.optString("sessionInfo") == sessionInfo) break
                }
            }
            code ?: throw AuthError.InvalidCode
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun refreshClaims() {
        publish(auth.currentUser, forceRefresh = true)
    }

    override suspend fun signOut() {
        auth.signOut()
        state.value = AuthState.SignedOut
    }

    private fun mapError(e: Exception): AuthError = when (e) {
        is AuthError -> e
        is FirebaseAuthInvalidCredentialsException -> when (e.errorCode) {
            "ERROR_INVALID_PHONE_NUMBER" -> AuthError.InvalidPhone
            "ERROR_SESSION_EXPIRED" -> AuthError.CodeExpired
            else -> AuthError.InvalidCode
        }
        is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
        is FirebaseNetworkException -> AuthError.Network
        is FirebaseAuthException -> AuthError.Unknown(e.errorCode)
        else -> AuthError.Unknown(e.message)
    }

    private companion object {
        const val CODE_TIMEOUT_SECONDS = 60L
    }
}
