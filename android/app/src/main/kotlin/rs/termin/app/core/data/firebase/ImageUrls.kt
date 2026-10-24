package rs.termin.app.core.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.termin.app.BuildConfig
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

enum class ImageSize { Thumb, Full }

/**
 * Pretvara putanju u Cloud Storage-u u URL za Coil. Za liste se koriste umanjene verzije
 * (`_thumb`), a kad je uključena „Ušteda podataka“, umanjene verzije se koriste svuda.
 */
@Singleton
class ImageUrls @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val bucket: String by lazy {
        FirebaseApp.getInstance().options.storageBucket ?: "${FirebaseApp.getInstance().options.projectId}.appspot.com"
    }

    private val base: String by lazy {
        if (BuildConfig.USE_EMULATORS) {
            "http://${BuildConfig.EMULATOR_HOST}:${EmulatorPorts.STORAGE}/v0/b/$bucket/o/"
        } else {
            "https://firebasestorage.googleapis.com/v0/b/$bucket/o/"
        }
    }

    private fun dataSaverOn(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    fun url(path: String?, size: ImageSize = ImageSize.Full): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        val effective = if (size == ImageSize.Thumb || dataSaverOn()) thumbPath(path) else path
        return base + URLEncoder.encode(effective, Charsets.UTF_8.name()).replace("+", "%20") + "?alt=media"
    }

    companion object {
        fun thumbPath(path: String): String {
            val dot = path.lastIndexOf('.')
            if (dot <= path.lastIndexOf('/')) return "${path}_thumb"
            return path.substring(0, dot) + "_thumb" + path.substring(dot)
        }
    }
}
