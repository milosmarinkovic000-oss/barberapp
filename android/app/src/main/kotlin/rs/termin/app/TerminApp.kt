package rs.termin.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.crashlytics
import dagger.hilt.android.HiltAndroidApp
import rs.termin.app.core.data.notifications.Notifier
import javax.inject.Inject

@HiltAndroidApp
class TerminApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notifier: Notifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // Crashlytics i App Check rade samo sa pravim Firebase projektom (ne u demo režimu).
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.USE_EMULATORS
        if (!BuildConfig.USE_EMULATORS) {
            // Debug build nad pravim projektom: debug token iz logcat-a se dodaje u Firebase konzoli.
            val factory = if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance() else PlayIntegrityAppCheckProviderFactory.getInstance()
            Firebase.appCheck.installAppCheckProviderFactory(factory)
        }
        notifier.createChannels()
    }

    /** Coil: keš u memoriji (20 % RAM-a) i na disku (100 MB), slike u veličini prikaza. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory()) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_PERCENT).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(DISK_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build(),
        )
    }

    private companion object {
        const val MEMORY_PERCENT = 0.2
        const val DISK_BYTES = 100L * 1024 * 1024
    }
}
