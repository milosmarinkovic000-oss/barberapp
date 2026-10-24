package rs.termin.app.core.designsystem.type

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rs.termin.app.R
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.TypographyChoice

/** Ugrađeni fontovi (OFL), samo potrebne težine i latinica sa č, ć, š, ž, đ. */
object TerminFonts {
    val Fraunces = FontFamily(
        Font(R.font.fraunces_medium, FontWeight.Medium),
        Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    )
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )
    val Playfair = FontFamily(
        Font(R.font.playfair_medium, FontWeight.Medium),
        Font(R.font.playfair_semibold, FontWeight.SemiBold),
    )
    val Manrope = FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
    )
    val DmSerifDisplay = FontFamily(Font(R.font.dm_serif_display_regular, FontWeight.Normal))
    val DmSans = FontFamily(
        Font(R.font.dm_sans_regular, FontWeight.Normal),
        Font(R.font.dm_sans_medium, FontWeight.Medium),
        Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    )
    val Poppins = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
    )

    data class Pair(val display: FontFamily, val body: FontFamily, val displayWeight: FontWeight, val serif: Boolean)

    fun pair(choice: TypographyChoice): Pair = when (choice) {
        TypographyChoice.FrauncesInter -> Pair(Fraunces, Inter, FontWeight.SemiBold, serif = true)
        TypographyChoice.PlayfairManrope -> Pair(Playfair, Manrope, FontWeight.SemiBold, serif = true)
        TypographyChoice.DmSerifDmSans -> Pair(DmSerifDisplay, DmSans, FontWeight.Normal, serif = true)
        TypographyChoice.InterOnly -> Pair(Inter, Inter, FontWeight.SemiBold, serif = false)
        TypographyChoice.Poppins -> Pair(Poppins, Poppins, FontWeight.SemiBold, serif = false)
    }
}

/** Tipografska skala dizajn sistema. */
@Immutable
data class TerminType(
    val display: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val callout: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val button: TextStyle,
    val price: TextStyle,
    val number: TextStyle,
    /** Da li se nadnaslovi (overline) pišu velikim slovima. */
    val uppercaseOverline: Boolean,
)

private data class Scale(
    val display: TextUnit,
    val title1: TextUnit,
    val title2: TextUnit,
    val title3: TextUnit,
    val body: TextUnit,
    val headingTracking: TextUnit,
    val overlineTracking: TextUnit,
    val sansHeadings: Boolean,
    val uppercaseOverline: Boolean,
)

/** Svaki stil ima svoju skalu: veličine, razmak slova i porodicu naslova. */
private fun scaleFor(style: StyleChoice): Scale = when (style) {
    StyleChoice.LuxuryMinimal -> Scale(36.sp, 28.sp, 22.sp, 18.sp, 16.sp, (-0.01).em, 0.14.em, sansHeadings = false, uppercaseOverline = true)
    StyleChoice.ModernClean -> Scale(32.sp, 26.sp, 20.sp, 17.sp, 16.sp, (-0.025).em, 0.04.em, sansHeadings = true, uppercaseOverline = false)
    StyleChoice.Editorial -> Scale(44.sp, 32.sp, 24.sp, 19.sp, 16.sp, (-0.02).em, 0.18.em, sansHeadings = false, uppercaseOverline = true)
    StyleChoice.DarkNoir -> Scale(38.sp, 28.sp, 22.sp, 18.sp, 16.sp, 0.01.em, 0.22.em, sansHeadings = false, uppercaseOverline = true)
    StyleChoice.SoftPastel -> Scale(32.sp, 26.sp, 21.sp, 18.sp, 17.sp, 0.em, 0.02.em, sansHeadings = true, uppercaseOverline = false)
    StyleChoice.Glass -> Scale(34.sp, 27.sp, 21.sp, 18.sp, 16.sp, (-0.015).em, 0.08.em, sansHeadings = true, uppercaseOverline = true)
}

fun terminType(choice: TypographyChoice, style: StyleChoice): TerminType {
    val pair = TerminFonts.pair(choice)
    val s = scaleFor(style)
    val heading = if (s.sansHeadings) pair.body else pair.display
    val headingWeight = if (s.sansHeadings) FontWeight.SemiBold else pair.displayWeight
    val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    fun headingStyle(size: TextUnit, lineHeight: Float) = TextStyle(
        fontFamily = heading,
        fontWeight = headingWeight,
        fontSize = size,
        lineHeight = size * lineHeight,
        letterSpacing = s.headingTracking,
        lineHeightStyle = trim,
    )
    val body = TextStyle(fontFamily = pair.body, fontWeight = FontWeight.Normal, fontSize = s.body, lineHeight = s.body * 1.5f, lineHeightStyle = trim)
    return TerminType(
        display = headingStyle(s.display, 1.12f),
        title1 = headingStyle(s.title1, 1.18f),
        title2 = headingStyle(s.title2, 1.24f),
        title3 = headingStyle(s.title3, 1.3f).copy(fontFamily = pair.body, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
        body = body,
        bodyStrong = body.copy(fontWeight = FontWeight.Medium),
        callout = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
        caption = body.copy(fontSize = 12.sp, lineHeight = 16.sp),
        overline = body.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.overlineTracking),
        button = body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
        price = body.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        number = body.copy(fontFeatureSettings = "tnum"),
        uppercaseOverline = s.uppercaseOverline,
    )
}

/** Material 3 tipografija izvedena iz skale (za ugrađene M3 komponente). */
fun TerminType.toMaterial(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = title1,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title2,
    titleLarge = title2,
    titleMedium = title3,
    titleSmall = bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = caption.copy(fontSize = 11.sp),
)
