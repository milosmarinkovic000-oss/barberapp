package rs.termin.app.core.data.user

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import rs.termin.app.BuildConfig
import rs.termin.app.core.data.firebase.FunctionsClient
import rs.termin.app.core.data.firebase.parseRoles
import rs.termin.app.core.data.firebase.snapshots
import rs.termin.app.core.data.firebase.toUserProfile
import rs.termin.app.core.data.images.ImageUploader
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.SyncResult
import rs.termin.app.core.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FunctionsClient,
    private val authRepository: AuthRepository,
    private val uploader: ImageUploader,
    private val reminders: ReminderScheduler,
) : UserRepository {

    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
    private fun userDoc() = firestore.collection("users").document(uid())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProfile(): Flow<UserProfile?> = authRepository.authState.flatMapLatest { state ->
        if (state is AuthState.SignedIn) {
            firestore.collection("users").document(state.uid).snapshots().map { snap ->
                if (snap.exists()) snap.toUserProfile() else null
            }
        } else {
            flowOf(null)
        }
    }

    override suspend fun syncAccount(language: String, name: String?): SyncResult {
        val data = buildMap<String, Any?> {
            put("language", language)
            if (!name.isNullOrBlank()) put("name", name)
        }
        val result = functions.call("syncAccount", data)
        val roles = (result["roles"] as? List<*>).orEmpty().filterIsInstance<String>()
        if (result["claimsChanged"] == true) authRepository.refreshClaims()
        return SyncResult(
            roles = parseRoles(roles),
            salonId = result["salonId"] as? String,
            stylistId = result["stylistId"] as? String,
            isNew = result["isNew"] == true,
        )
    }

    override suspend fun updateName(name: String) {
        userDoc().update(mapOf("name" to name.trim(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun updatePhoto(uri: Uri) {
        val path = uploader.upload(uri, "users/${uid()}/avatar.webp")
        userDoc().update(mapOf("photoPath" to path, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun setLanguage(language: String) {
        userDoc().update(mapOf("language" to language, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        userDoc().update(mapOf("notificationsEnabled" to enabled, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun toggleFavoriteSalon(salonId: String, favorite: Boolean) {
        val op = if (favorite) FieldValue.arrayUnion(salonId) else FieldValue.arrayRemove(salonId)
        userDoc().update(mapOf("favoriteSalonIds" to op, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun toggleFavoriteStylist(key: String, favorite: Boolean) {
        val op = if (favorite) FieldValue.arrayUnion(key) else FieldValue.arrayRemove(key)
        userDoc().update(mapOf("favoriteStylistKeys" to op, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    /**
     * FCM registracija preko Firebase Installation ID-a (FID); FID se čuva u profilu (fcmTokens).
     * Emulator nema FCM, pa se u demo režimu preskače.
     */
    override suspend fun registerPushToken() {
        if (BuildConfig.USE_EMULATORS || auth.currentUser == null) return
        runCatching { FirebaseMessaging.getInstance().register().await() }
        val fid = runCatching { FirebaseInstallations.getInstance().id.await() }.getOrNull() ?: return
        savePushId(fid)
    }

    override suspend fun savePushId(installationId: String) {
        if (BuildConfig.USE_EMULATORS || auth.currentUser == null) return
        userDoc().update(mapOf("fcmTokens" to FieldValue.arrayUnion(installationId))).await()
    }

    override suspend fun createSalon(name: String, city: String, address: String, phone: String, location: GeoLocation): String {
        val result = functions.call(
            "createSalon",
            mapOf("name" to name, "city" to city, "address" to address, "phone" to phone, "lat" to location.latitude, "lng" to location.longitude),
        )
        authRepository.refreshClaims()
        return result["salonId"] as String
    }

    override suspend fun deleteAccount() {
        functions.call("deleteAccount", mapOf("confirm" to true))
        reminders.cancelAll()
        authRepository.signOut()
    }
}
