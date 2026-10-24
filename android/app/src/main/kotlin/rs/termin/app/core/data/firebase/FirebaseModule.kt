package rs.termin.app.core.data.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.termin.app.BuildConfig
import javax.inject.Singleton

/** Region Cloud Functions (isti kao na serveru). */
const val FUNCTIONS_REGION = "europe-west1"

/** Portovi Firebase Emulator Suite-a (firebase.json). */
object EmulatorPorts {
    const val AUTH = 9099
    const val FIRESTORE = 8080
    const val FUNCTIONS = 5001
    const val STORAGE = 9199
}

/**
 * Firebase servisi. Debug build se sam povezuje na emulatore (10.0.2.2 je računar viđen iz
 * Android emulatora); release koristi pravi projekat iz google-services.json.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun auth(): FirebaseAuth = Firebase.auth.apply {
        if (BuildConfig.USE_EMULATORS) {
            useEmulator(BuildConfig.EMULATOR_HOST, EmulatorPorts.AUTH)
            // Emulator ne traži reCAPTCHA / Play Integrity za SMS prijavu.
            firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        }
    }

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = Firebase.firestore.apply {
        if (BuildConfig.USE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, EmulatorPorts.FIRESTORE)
        // Offline keš: prikaz poslednjih podataka i bez interneta.
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(CACHE_BYTES) })
        }
    }

    @Provides
    @Singleton
    fun functions(): FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION).apply {
        if (BuildConfig.USE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, EmulatorPorts.FUNCTIONS)
    }

    @Provides
    @Singleton
    fun storage(): FirebaseStorage = Firebase.storage.apply {
        if (BuildConfig.USE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, EmulatorPorts.STORAGE)
    }

    private const val CACHE_BYTES = 50L * 1024 * 1024
}
