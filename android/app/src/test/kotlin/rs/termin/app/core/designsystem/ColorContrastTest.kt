package rs.termin.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.termin.app.core.designsystem.color.ColorMath
import rs.termin.app.core.designsystem.color.SalonAccentOptions
import rs.termin.app.core.designsystem.color.TerminColors
import rs.termin.app.core.designsystem.color.accentShades
import rs.termin.app.core.designsystem.color.buildColors
import rs.termin.app.core.designsystem.color.paletteSpec
import rs.termin.app.core.designsystem.color.salonDarkAccent

/**
 * Kontrast za svaku kombinaciju stila, palete, nijanse i teme: tekst ≥ 4.5:1, ivice i ikonice
 * ≥ 3:1, tekst na akcentu (beo ili taman, automatski) ≥ 4.5:1. Važi i za 8 boja salona.
 */
class ColorContrastTest {
    private val text = 4.5
    private val ui = 3.0

    private fun check(label: String, fg: Color, bg: Color, min: Double) {
        val ratio = ColorMath.contrast(fg, bg)
        assertTrue("$label: ${"%.2f".format(ratio)} < $min", ratio >= min - 0.01)
    }

    private fun verify(name: String, c: TerminColors) {
        check("$name text/background", c.text, c.background, text)
        check("$name text/surface", c.text, c.surface, text)
        check("$name textSecondary/background", c.textSecondary, c.background, text)
        check("$name textSecondary/surfaceMuted", c.textSecondary, c.surfaceMuted, text)
        check("$name accentText/background", c.accentText, c.background, text)
        check("$name accentText/surface", c.accentText, c.surface, text)
        check("$name onAccent/accent", c.onAccent, c.accent, text)
        check("$name onAccentSoft/accentSoft", c.onAccentSoft, c.accentSoft, text)
        check("$name outline/surface", c.outline, c.surface, ui)
        check("$name outline/background", c.outline, c.background, ui)
        check("$name onSuccessSoft", c.onSuccessSoft, c.successSoft, text)
        check("$name onWarningSoft", c.onWarningSoft, c.warningSoft, text)
        check("$name onErrorSoft", c.onErrorSoft, c.errorSoft, text)
        check("$name error/surface", c.error, c.surface, text)
        check("$name onError/error", c.onError, c.error, text)
    }

    @Test
    fun `sve palete, nijanse i stilovi u obe teme`() {
        val palettes = PaletteChoice.entries.filter { it != PaletteChoice.DeviceDynamic }
        for (style in StyleChoice.entries) for (palette in palettes) for (dark in listOf(false, true)) {
            val spec = paletteSpec(palette, 0x8C6A3F)
            val variants = listOf(spec.lightAccent to spec.darkAccent) +
                accentShades(spec.lightAccent).zip(accentShades(spec.darkAccent))
            variants.forEachIndexed { i, (light, darkAccent) ->
                verify("$style/$palette/shade$i/${if (dark) "dark" else "light"}", buildColors(light, darkAccent, spec.warm, dark, style))
            }
        }
    }

    @Test
    fun `boje salona u svetloj i tamnoj temi`() {
        for (style in StyleChoice.entries) for (accent in SalonAccentOptions) for (dark in listOf(false, true)) {
            verify("$style/salon-${accent.value}/$dark", buildColors(accent, salonDarkAccent(accent), true, dark, style))
        }
    }

    @Test
    fun `sopstvena boja bilo kog tona`() {
        for (rgb in listOf(0xFFFF00L, 0x00FFFFL, 0x777777L, 0x101010L, 0xF0F0F0L, 0xE4572EL)) {
            val spec = paletteSpec(PaletteChoice.Custom, rgb)
            for (dark in listOf(false, true)) {
                verify("custom-${rgb.toString(16)}/$dark", buildColors(spec.lightAccent, spec.darkAccent, spec.warm, dark, StyleChoice.LuxuryMinimal))
            }
        }
    }

    @Test
    fun `izbor u aplikaciji ima 4 do 6 nijansi`() {
        for (palette in PaletteChoice.entries.filter { it != PaletteChoice.DeviceDynamic && it != PaletteChoice.Custom }) {
            val spec = paletteSpec(palette, 0)
            val count = accentShades(spec.lightAccent).size
            assertTrue("$palette: $count", count in 4..6)
        }
    }
}
