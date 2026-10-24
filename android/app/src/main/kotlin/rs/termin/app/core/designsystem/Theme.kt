package rs.termin.app.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import rs.termin.app.core.designsystem.color.TerminColors
import rs.termin.app.core.designsystem.color.accentShades
import rs.termin.app.core.designsystem.color.buildColors
import rs.termin.app.core.designsystem.color.fromDynamicScheme
import rs.termin.app.core.designsystem.color.paletteSpec
import rs.termin.app.core.designsystem.color.salonDarkAccent
import rs.termin.app.core.designsystem.color.toColorScheme
import rs.termin.app.core.designsystem.tokens.Motion
import rs.termin.app.core.designsystem.tokens.Radii
import rs.termin.app.core.designsystem.tokens.Spacing
import rs.termin.app.core.designsystem.tokens.Surfaces
import rs.termin.app.core.designsystem.tokens.motionFor
import rs.termin.app.core.designsystem.tokens.radiiFor
import rs.termin.app.core.designsystem.tokens.spacingFor
import rs.termin.app.core.designsystem.tokens.surfacesFor
import rs.termin.app.core.designsystem.tokens.toMaterialShapes
import rs.termin.app.core.designsystem.type.TerminType
import rs.termin.app.core.designsystem.type.terminType
import rs.termin.app.core.designsystem.type.toMaterial

/** Korisnikov izbor izgleda (Podešavanja → Izgled). */
@Immutable
data class Appearance(
    val themeMode: ThemeMode = ThemeConfig.Default.initialThemeMode,
    /** Indeks nijanse iz [accentShades]; null = osnovni akcenat palete. */
    val accentShade: Int? = null,
)

/** Stanje uređaja koje utiče na pokret i efekte. */
@Immutable
data class DeviceCapabilities(
    val reduceMotion: Boolean = false,
    val lowPower: Boolean = false,
    val blurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
)

@Immutable
data class ThemeInputs(
    val config: ThemeConfig,
    val appearance: Appearance,
    val dark: Boolean,
    val device: DeviceCapabilities,
)

private val LocalColors = staticCompositionLocalOf { buildColors(Color(0xFFB08D57), Color(0xFFD4B47A), true, false, StyleChoice.LuxuryMinimal) }
private val LocalType = staticCompositionLocalOf { terminType(TypographyChoice.FrauncesInter, StyleChoice.LuxuryMinimal) }
private val LocalSpacing = staticCompositionLocalOf { spacingFor(Density.Airy, StyleChoice.LuxuryMinimal) }
private val LocalRadii = staticCompositionLocalOf { radiiFor(Roundness.Moderate, StyleChoice.LuxuryMinimal) }
private val LocalSurfaces = staticCompositionLocalOf { surfacesFor(StyleChoice.LuxuryMinimal) }
private val LocalMotion = staticCompositionLocalOf { motionFor(MotionLevel.Subtle, reduceMotion = false, lowPower = false) }
private val LocalInputs = staticCompositionLocalOf { ThemeInputs(ThemeConfig.Default, Appearance(), false, DeviceCapabilities()) }

/** Pristup tokenima dizajn sistema iz bilo koje komponente. */
object TerminTheme {
    val colors: TerminColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: TerminType @Composable @ReadOnlyComposable get() = LocalType.current
    val spacing: Spacing @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val radii: Radii @Composable @ReadOnlyComposable get() = LocalRadii.current
    val surfaces: Surfaces @Composable @ReadOnlyComposable get() = LocalSurfaces.current
    val motion: Motion @Composable @ReadOnlyComposable get() = LocalMotion.current
    val inputs: ThemeInputs @Composable @ReadOnlyComposable get() = LocalInputs.current
    val config: ThemeConfig @Composable @ReadOnlyComposable get() = LocalInputs.current.config
    val features: Features @Composable @ReadOnlyComposable get() = LocalInputs.current.config.features

    /** Zamućenje (stil Staklo) samo na Android 12+, bez uštede baterije. */
    val useBlur: Boolean
        @Composable @ReadOnlyComposable get() {
            val inputs = LocalInputs.current
            return LocalSurfaces.current.glass && inputs.device.blurSupported && !inputs.device.lowPower
        }
}

fun readDeviceCapabilities(context: Context): DeviceCapabilities {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val power = context.getSystemService(PowerManager::class.java)
    val activity = context.getSystemService(ActivityManager::class.java)
    return DeviceCapabilities(
        reduceMotion = scale == 0f,
        lowPower = (power?.isPowerSaveMode == true) || (activity?.isLowRamDevice == true),
    )
}

/** Osnovni akcenat po paleti i korisnikovoj nijansi (svetla, tamna). */
fun baseAccents(config: ThemeConfig, appearance: Appearance): Pair<Color, Color> {
    val spec = paletteSpec(config.palette, config.customAccent)
    val index = appearance.accentShade
    if (index == null || !config.features.inAppAppearance) return spec.lightAccent to spec.darkAccent
    val light = accentShades(spec.lightAccent).getOrElse(index) { spec.lightAccent }
    val dark = accentShades(spec.darkAccent).getOrElse(index) { spec.darkAccent }
    return light to dark
}

@Composable
fun resolveDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Tema aplikacije. [accentOverride] postavlja akcentnu boju salona (stranica salona i tok
 * zakazivanja kod tog salona); ostatak aplikacije zadržava glavnu paletu.
 */
@Composable
fun TerminTheme(
    config: ThemeConfig = ThemeConfig.Default,
    appearance: Appearance = Appearance(themeMode = config.initialThemeMode),
    device: DeviceCapabilities? = null,
    accentOverride: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val capabilities = device ?: remember(context, inspection) {
        if (inspection) DeviceCapabilities() else readDeviceCapabilities(context)
    }
    val dark = resolveDark(appearance.themeMode) || config.style == StyleChoice.DarkNoir
    val colors = remember(config, appearance, dark, accentOverride, capabilities) {
        val spec = paletteSpec(config.palette, config.customAccent)
        when {
            accentOverride != null -> buildColors(accentOverride, salonDarkAccent(accentOverride), spec.warm, dark, config.style)
            config.palette == PaletteChoice.DeviceDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !inspection -> {
                val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                fromDynamicScheme(scheme, dark, config.style)
            }
            else -> {
                val (light, darkAccent) = baseAccents(config, appearance)
                buildColors(light, darkAccent, spec.warm, dark, config.style)
            }
        }
    }
    val type = remember(config.typography, config.style) { terminType(config.typography, config.style) }
    val spacing = remember(config.density, config.style) { spacingFor(config.density, config.style) }
    val radii = remember(config.roundness, config.style) { radiiFor(config.roundness, config.style) }
    val surfaces = remember(config.style) { surfacesFor(config.style) }
    val motion = remember(config.motion, capabilities) { motionFor(config.motion, capabilities.reduceMotion, capabilities.lowPower) }
    val inputs = ThemeInputs(config, appearance, dark, capabilities)

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalType provides type,
        LocalSpacing provides spacing,
        LocalRadii provides radii,
        LocalSurfaces provides surfaces,
        LocalMotion provides motion,
        LocalInputs provides inputs,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = type.toMaterial(),
            shapes = radii.toMaterialShapes(),
            content = content,
        )
    }
}

/** Akcentna boja salona važi samo unutar ovog bloka (stranica salona, zakazivanje). */
@Composable
fun SalonAccentTheme(accent: Color?, content: @Composable () -> Unit) {
    val inputs = LocalInputs.current
    if (accent == null || !inputs.config.features.salonBranding) {
        content()
        return
    }
    TerminTheme(
        config = inputs.config,
        appearance = inputs.appearance,
        device = inputs.device,
        accentOverride = accent,
        content = content,
    )
}
