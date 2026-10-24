package rs.termin.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.termin.app.core.data.prefs.ThemeConfigCodec
import rs.termin.app.core.designsystem.Density
import rs.termin.app.core.designsystem.MotionLevel
import rs.termin.app.core.designsystem.PaletteChoice
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.designsystem.ThemeMode

class ThemeConfigCodecTest {
    @Test
    fun `izbor se cuva i vraca bez gubitka`() {
        val config = ThemeConfig.Default.copy(
            style = StyleChoice.Glass,
            palette = PaletteChoice.Custom,
            customAccent = 0x123456,
            density = Density.Compact,
            motion = MotionLevel.Expressive,
            initialThemeMode = ThemeMode.Dark,
        )
        assertEquals(config, ThemeConfigCodec.decode(ThemeConfigCodec.encode(config)))
    }

    @Test
    fun `nepoznate vrednosti padaju na podrazumevane`() {
        val decoded = ThemeConfigCodec.decode("style=Nepostojeci;palette=Emerald;accent=zz")
        assertEquals(StyleChoice.LuxuryMinimal, decoded?.style)
        assertEquals(PaletteChoice.Emerald, decoded?.palette)
        assertEquals(ThemeConfig.Default.customAccent, decoded?.customAccent)
        assertNull(ThemeConfigCodec.decode(""))
    }
}
