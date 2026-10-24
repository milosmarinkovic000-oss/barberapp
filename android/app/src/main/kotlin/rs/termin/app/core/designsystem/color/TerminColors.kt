package rs.termin.app.core.designsystem.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import rs.termin.app.core.designsystem.PaletteChoice
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.color.ColorMath.TEXT_CONTRAST
import rs.termin.app.core.designsystem.color.ColorMath.UI_CONTRAST
import rs.termin.app.core.designsystem.color.ColorMath.bestOn
import rs.termin.app.core.designsystem.color.ColorMath.ensureContrast
import rs.termin.app.core.designsystem.color.ColorMath.mix

/** Boje dizajn sistema. Sve kombinacije teksta imaju kontrast ≥ 4.5:1, a ivice i ikonice ≥ 3:1. */
@Immutable
data class TerminColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceMuted: Color,
    val text: Color,
    val textSecondary: Color,
    val outline: Color,
    val divider: Color,
    /** Brend akcenat za popunjene površine (dugmad, izabrani elementi). */
    val accent: Color,
    val onAccent: Color,
    /** Akcenat za tekst, linkove, ikonice i ivice (dovoljno taman/svetao za 4.5:1). */
    val accentText: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val onSuccessSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val onWarningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val onErrorSoft: Color,
    val onError: Color,
    val scrim: Color,
    val skeleton: Color,
    val skeletonHighlight: Color,
    /** Poluprovidna površina za stil „Staklo“ (bez zamućenja na slabijim telefonima). */
    val glass: Color,
)

/** Neutralna osnova palete (topla ili hladna). */
private data class Neutrals(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceMuted: Color,
    val text: Color,
    val textSecondary: Color,
    val outline: Color,
    val divider: Color,
)

private val WarmLight = Neutrals(
    background = Color(0xFFFAF8F5), surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF2EEE8), text = Color(0xFF1C1B1A), textSecondary = Color(0xFF6E6A64),
    outline = Color(0xFF8C867E), divider = Color(0xFFE7E1D8),
)
private val WarmDark = Neutrals(
    background = Color(0xFF121110), surface = Color(0xFF1C1A18), surfaceRaised = Color(0xFF242120),
    surfaceMuted = Color(0xFF2A2724), text = Color(0xFFF2EEE8), textSecondary = Color(0xFFB5AFA6),
    outline = Color(0xFF857E75), divider = Color(0xFF332F2B),
)
private val CoolLight = Neutrals(
    background = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEDF0F3), text = Color(0xFF1A1C1E), textSecondary = Color(0xFF5E646C),
    outline = Color(0xFF858B93), divider = Color(0xFFDDE2E7),
)
private val CoolDark = Neutrals(
    background = Color(0xFF101214), surface = Color(0xFF181B1E), surfaceRaised = Color(0xFF202428),
    surfaceMuted = Color(0xFF262A2F), text = Color(0xFFEDF0F3), textSecondary = Color(0xFFA9B0B8),
    outline = Color(0xFF7A8189), divider = Color(0xFF2C3136),
)

/** Akcenti palete 1–5 (svetla / tamna tema) iz odeljka 0. */
data class PaletteSpec(val lightAccent: Color, val darkAccent: Color, val warm: Boolean)

fun paletteSpec(choice: PaletteChoice, customAccent: Long): PaletteSpec = when (choice) {
    PaletteChoice.Champagne, PaletteChoice.DeviceDynamic -> PaletteSpec(Color(0xFFB08D57), Color(0xFFD4B47A), warm = true)
    PaletteChoice.Emerald -> PaletteSpec(Color(0xFF0F6B5C), Color(0xFF3FB59E), warm = false)
    PaletteChoice.Bordeaux -> PaletteSpec(Color(0xFF7A1F3D), Color(0xFFD46A8C), warm = true)
    PaletteChoice.MidnightBlue -> PaletteSpec(Color(0xFF1E3A8A), Color(0xFF7C9CF5), warm = false)
    PaletteChoice.GraphiteCoral -> PaletteSpec(Color(0xFFE4572E), Color(0xFFFF8A65), warm = false)
    PaletteChoice.Custom -> {
        val accent = Color(0xFF000000 or (customAccent and 0xFFFFFF))
        val dark = ColorMath.withLightness(accent, maxOf(ColorMath.toHsl(accent)[2], 0.68f))
        PaletteSpec(accent, dark, ColorMath.isWarm(accent))
    }
}

/**
 * Gradi boje za temu: neutralna osnova (topla/hladna, po paleti) + akcenat. Stil menja
 * osnovu (pastel boji površine akcentom, noir je uvek taman, staklo dobija providne površine).
 */
fun buildColors(accentLight: Color, accentDark: Color, warm: Boolean, dark: Boolean, style: StyleChoice): TerminColors {
    val isDark = dark || style == StyleChoice.DarkNoir
    var n = when {
        warm && isDark -> WarmDark
        warm -> WarmLight
        isDark -> CoolDark
        else -> CoolLight
    }
    val rawAccent = if (isDark) accentDark else accentLight
    if (style == StyleChoice.SoftPastel && !isDark) {
        n = n.copy(
            background = mix(n.background, rawAccent, 0.06f),
            surface = mix(Color.White, rawAccent, 0.025f),
            surfaceMuted = mix(n.surfaceMuted, rawAccent, 0.12f),
            divider = mix(n.divider, rawAccent, 0.15f),
        )
    }
    if (style == StyleChoice.DarkNoir) {
        n = n.copy(
            background = Color(0xFF0B0A0A),
            surface = Color(0xFF151413),
            surfaceRaised = Color(0xFF1D1B1A),
            surfaceMuted = Color(0xFF232120),
        )
    }
    if (style == StyleChoice.ModernClean) {
        n = n.copy(text = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111111))
    }
    // Sporedni tekst i ivice moraju da zadovolje kontrast i na podlozi i na površini.
    val textSecondary = ensureContrast(ensureContrast(n.textSecondary, n.background, TEXT_CONTRAST), n.surfaceMuted, TEXT_CONTRAST)
    val outline = ensureContrast(ensureContrast(n.outline, n.background, UI_CONTRAST), n.surface, UI_CONTRAST)

    // Popunjeni akcenat: tekst na njemu je beo ili taman (veći kontrast); ako ni jedan ne
    // dostiže 4.5:1, akcenat se zatamnjuje (ili posvetljuje) dok ne dostigne.
    val accent = accentFill(rawAccent)
    val onAccent = bestOn(accent, dark = Color(0xFF1C1B1A))
    val accentText = ensureContrast(ensureContrast(rawAccent, n.background, TEXT_CONTRAST), n.surface, TEXT_CONTRAST)
    val accentSoft = mix(n.surface, rawAccent, if (isDark) 0.22f else 0.14f)
    val onAccentSoft = ensureContrast(accentText, accentSoft, TEXT_CONTRAST)

    fun harmonize(base: Color) = mix(base, rawAccent, 0.08f)
    val successBase = harmonize(if (isDark) Color(0xFF6FCF97) else Color(0xFF2E7D4F))
    val warningBase = harmonize(if (isDark) Color(0xFFF2C14E) else Color(0xFF8A5A00))
    val errorBase = harmonize(if (isDark) Color(0xFFF2B8B5) else Color(0xFFB3261E))
    val success = ensureContrast(ensureContrast(successBase, n.background, TEXT_CONTRAST), n.surface, TEXT_CONTRAST)
    val warning = ensureContrast(ensureContrast(warningBase, n.background, TEXT_CONTRAST), n.surface, TEXT_CONTRAST)
    val error = ensureContrast(ensureContrast(errorBase, n.background, TEXT_CONTRAST), n.surface, TEXT_CONTRAST)
    val successSoft = mix(n.surface, success, if (isDark) 0.22f else 0.12f)
    val warningSoft = mix(n.surface, warning, if (isDark) 0.22f else 0.12f)
    val errorSoft = mix(n.surface, error, if (isDark) 0.22f else 0.12f)

    return TerminColors(
        isDark = isDark,
        background = n.background,
        surface = n.surface,
        surfaceRaised = n.surfaceRaised,
        surfaceMuted = n.surfaceMuted,
        text = n.text,
        textSecondary = textSecondary,
        outline = outline,
        divider = n.divider,
        accent = accent,
        onAccent = onAccent,
        accentText = accentText,
        accentSoft = accentSoft,
        onAccentSoft = onAccentSoft,
        success = success,
        successSoft = successSoft,
        onSuccessSoft = ensureContrast(success, successSoft, TEXT_CONTRAST),
        warning = warning,
        warningSoft = warningSoft,
        onWarningSoft = ensureContrast(warning, warningSoft, TEXT_CONTRAST),
        error = error,
        errorSoft = errorSoft,
        onErrorSoft = ensureContrast(error, errorSoft, TEXT_CONTRAST),
        onError = bestOn(error),
        scrim = Color.Black.copy(alpha = 0.5f),
        skeleton = mix(n.surfaceMuted, n.text, 0.04f),
        skeletonHighlight = mix(n.surfaceMuted, n.surface, 0.7f),
        glass = n.surface.copy(alpha = if (isDark) 0.72f else 0.78f),
    )
}

/** Akcenat za popunjene površine: obezbeđuje 4.5:1 sa boljim od belog/tamnog teksta. */
fun accentFill(raw: Color): Color {
    val dark = Color(0xFF1C1B1A)
    if (maxOf(ColorMath.contrast(raw, dark), ColorMath.contrast(raw, Color.White)) >= TEXT_CONTRAST) return raw
    // Srednje tonove pomeramo ka tamnijem (beli tekst) — tako ostaju prepoznatljivi.
    return ensureContrast(raw, Color.White, TEXT_CONTRAST)
}

/** Material 3 šema izvedena iz boja dizajn sistema (za ugrađene M3 komponente). */
fun TerminColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accentText,
        onPrimary = bestOn(accentText),
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = accentText,
        onSecondary = bestOn(accentText),
        secondaryContainer = accentSoft,
        onSecondaryContainer = onAccentSoft,
        tertiary = success,
        onTertiary = bestOn(success),
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceMuted,
        surfaceBright = surface,
        surfaceDim = background,
        inverseSurface = text,
        inverseOnSurface = background,
        inversePrimary = accent,
        outline = outline,
        outlineVariant = divider,
        error = error,
        onError = onError,
        errorContainer = errorSoft,
        onErrorContainer = onErrorSoft,
        scrim = scrim,
    )
}

/** Paleta 6: boje telefona (Android 12+) prevedene u boje dizajn sistema. */
fun fromDynamicScheme(scheme: ColorScheme, isDark: Boolean, style: StyleChoice): TerminColors {
    val built = buildColors(scheme.primary, scheme.primary, ColorMath.isWarm(scheme.primary), isDark, style)
    return built.copy(
        background = scheme.background,
        surface = scheme.surface,
        surfaceRaised = scheme.surfaceContainer,
        surfaceMuted = scheme.surfaceContainerHighest,
        text = scheme.onSurface,
        textSecondary = ensureContrast(scheme.onSurfaceVariant, scheme.surfaceContainerHighest, TEXT_CONTRAST),
        outline = ensureContrast(scheme.outline, scheme.surface, UI_CONTRAST),
        divider = scheme.outlineVariant,
        accentText = ensureContrast(scheme.primary, scheme.surface, TEXT_CONTRAST),
    )
}

/** 4–6 nijansi akcenta izabrane palete za Podešavanja → Izgled (sve proverenog kontrasta). */
fun accentShades(base: Color): List<Color> {
    val hsl = ColorMath.toHsl(base)
    return listOf(-0.14f, -0.07f, 0f, 0.06f, 0.12f)
        .map { delta -> ColorMath.fromHsl(hsl[0], hsl[1], (hsl[2] + delta).coerceIn(0.12f, 0.88f)) }
        .map { accentFill(it) }
        .distinct()
}

/** Akcentne boje koje vlasnik može da izabere za svoj salon (ista lista kao na serveru). */
val SalonAccentOptions: List<Color> = listOf(
    Color(0xFF8C6A3F), Color(0xFF7A1F3D), Color(0xFF0F6B5C), Color(0xFF1E3A8A),
    Color(0xFFB4432B), Color(0xFF5B4B8A), Color(0xFF3F5E2A), Color(0xFF6B5B4E),
)

/** Za tamnu temu akcenat salona se posvetljuje da bi ostao čitljiv na tamnoj podlozi. */
fun salonDarkAccent(accent: Color): Color =
    ColorMath.withLightness(accent, maxOf(ColorMath.toHsl(accent)[2], 0.66f))
