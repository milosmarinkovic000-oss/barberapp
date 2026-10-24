package rs.termin.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import rs.termin.app.core.data.firebase.ImageUrls
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.resolveDark
import rs.termin.app.core.ui.LocalFormatter
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.rememberFormatter
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()

    @Inject lateinit var imageUrls: ImageUrls

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Splash ostaje dok se ne zna stanje prijave i podešavanja (bez treptanja praznog ekrana).
        splash.setKeepOnScreenCondition { viewModel.uiState.value.phase == AppPhase.Loading }
        if (savedInstanceState == null) consumeDeepLink(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = resolveDark(state.appearance.themeMode) || state.themeConfig.style == StyleChoice.DarkNoir
            // Ikonice sistemskih traka prate temu aplikacije (i kad se razlikuje od teme telefona).
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TerminTheme(config = state.themeConfig, appearance = state.appearance) {
                CompositionLocalProvider(
                    LocalFormatter provides rememberFormatter(),
                    LocalImageUrls provides imageUrls,
                ) {
                    AppRoot(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDeepLink(intent)
    }

    /**
     * Link iz obaveštenja, deljeni link salona ili povratak sa OAuth-a čeka da glavni ekran bude
     * spreman (npr. posle prijave); podaci se brišu iz intenta da ga navigacija ne obradi dvaput.
     */
    private fun consumeDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        viewModel.onDeepLink(data)
        intent.data = null
    }
}
