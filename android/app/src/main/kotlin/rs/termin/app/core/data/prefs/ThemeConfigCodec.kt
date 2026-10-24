package rs.termin.app.core.data.prefs

import rs.termin.app.core.designsystem.Density
import rs.termin.app.core.designsystem.IconStyle
import rs.termin.app.core.designsystem.MotionLevel
import rs.termin.app.core.designsystem.NavigationStyle
import rs.termin.app.core.designsystem.PaletteChoice
import rs.termin.app.core.designsystem.Roundness
import rs.termin.app.core.designsystem.SalonCardStyle
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.designsystem.ThemeMode
import rs.termin.app.core.designsystem.TypographyChoice

/** Čuvanje izbora sa ekrana „Pregled stila“ (samo debug) kao „ključ=vrednost;...“. */
object ThemeConfigCodec {
    fun encode(c: ThemeConfig): String = listOf(
        "style" to c.style.name,
        "palette" to c.palette.name,
        "accent" to c.customAccent.toString(16),
        "type" to c.typography.name,
        "round" to c.roundness.name,
        "density" to c.density.name,
        "motion" to c.motion.name,
        "icons" to c.iconStyle.name,
        "card" to c.salonCard.name,
        "nav" to c.navigation.name,
        "theme" to c.initialThemeMode.name,
    ).joinToString(";") { "${it.first}=${it.second}" }

    fun decode(text: String): ThemeConfig? {
        val map = text.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
        }.toMap()
        if (map.isEmpty()) return null
        val d = ThemeConfig.Default
        fun <T : Enum<T>> pick(key: String, values: Array<T>, default: T): T = values.firstOrNull { it.name == map[key] } ?: default
        return d.copy(
            style = pick("style", StyleChoice.entries.toTypedArray(), d.style),
            palette = pick("palette", PaletteChoice.entries.toTypedArray(), d.palette),
            customAccent = map["accent"]?.toLongOrNull(16) ?: d.customAccent,
            typography = pick("type", TypographyChoice.entries.toTypedArray(), d.typography),
            roundness = pick("round", Roundness.entries.toTypedArray(), d.roundness),
            density = pick("density", Density.entries.toTypedArray(), d.density),
            motion = pick("motion", MotionLevel.entries.toTypedArray(), d.motion),
            iconStyle = pick("icons", IconStyle.entries.toTypedArray(), d.iconStyle),
            salonCard = pick("card", SalonCardStyle.entries.toTypedArray(), d.salonCard),
            navigation = pick("nav", NavigationStyle.entries.toTypedArray(), d.navigation),
            initialThemeMode = pick("theme", ThemeMode.entries.toTypedArray(), d.initialThemeMode),
        )
    }
}
