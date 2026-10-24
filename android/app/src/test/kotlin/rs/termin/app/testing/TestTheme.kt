package rs.termin.app.testing

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import rs.termin.app.core.data.firebase.ImageUrls
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.DeviceCapabilities
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.designsystem.ThemeMode
import rs.termin.app.core.ui.Formatter
import rs.termin.app.core.ui.LocalFormatter
import rs.termin.app.core.ui.LocalImageUrls

/** Firebase bez mreže, samo da bi se iz putanje slika napravio URL (slike se u testu ne učitavaju). */
fun initTestFirebase(context: Context) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setProjectId("demo-termin")
                .setApiKey("test-key")
                .setStorageBucket("demo-termin.appspot.com")
                .build(),
        )
    }
}

/** Tema i lokalni servisi kao u aplikaciji, bez animacija (stabilni testovi i snimci). */
@Composable
fun TestApp(config: ThemeConfig = ThemeConfig.Default, dark: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    initTestFirebase(context)
    TerminTheme(
        config = config,
        appearance = Appearance(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light),
        device = DeviceCapabilities(reduceMotion = true, lowPower = false, blurSupported = true),
    ) {
        CompositionLocalProvider(LocalFormatter provides Formatter(context), LocalImageUrls provides ImageUrls(context)) {
            content()
        }
    }
}
