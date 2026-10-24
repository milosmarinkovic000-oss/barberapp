package rs.termin.app.core.designsystem.color

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x pomoćne funkcije za proveru i ispravljanje kontrasta. */
object ColorMath {
    const val TEXT_CONTRAST = 4.5
    const val UI_CONTRAST = 3.0

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    /** Odnos kontrasta između dve neprozirne boje (1..21). */
    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Tekst na boji: beo ili taman, šta god daje veći kontrast. */
    fun bestOn(background: Color, dark: Color = Color(0xFF1C1B1A), light: Color = Color.White): Color =
        if (contrast(background, dark) >= contrast(background, light)) dark else light

    fun toHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2f
        if (maxC == minC) return floatArrayOf(0f, 0f, l)
        val d = maxC - minC
        val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
        val h = when (maxC) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return floatArrayOf(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }

    fun withLightness(color: Color, lightness: Float): Color {
        val (h, s, _) = toHsl(color)
        return fromHsl(h, s, lightness.coerceIn(0f, 1f), color.alpha)
    }

    fun shiftLightness(color: Color, delta: Float): Color {
        val hsl = toHsl(color)
        return fromHsl(hsl[0], hsl[1], (hsl[2] + delta).coerceIn(0f, 1f), color.alpha)
    }

    /**
     * Pomera svetlinu boje (tamnije na svetloj podlozi, svetlije na tamnoj) dok ne postigne
     * traženi kontrast prema podlozi. Ako je kontrast već dovoljan, vraća istu boju.
     */
    fun ensureContrast(color: Color, against: Color, target: Double): Color {
        if (contrast(color, against) >= target) return color
        val darken = luminance(against) > 0.18
        val hsl = toHsl(color)
        var l = hsl[2]
        repeat(100) {
            l = if (darken) l - 0.01f else l + 0.01f
            val candidate = fromHsl(hsl[0], hsl[1], l.coerceIn(0f, 1f))
            if (contrast(candidate, against) >= target || l <= 0f || l >= 1f) return candidate
        }
        return fromHsl(hsl[0], hsl[1], l.coerceIn(0f, 1f))
    }

    /** Linearna mešavina dve boje (t = udeo boje [b]). */
    fun mix(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )

    /** Topla nijansa: crvene, narandžaste, žute i ljubičasto-crvene. */
    fun isWarm(color: Color): Boolean {
        val h = toHsl(color)[0]
        return h < 75f || h > 300f
    }
}
