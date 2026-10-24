package rs.termin.app.core.designsystem.tokens

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.Density
import rs.termin.app.core.designsystem.MotionLevel
import rs.termin.app.core.designsystem.Roundness
import rs.termin.app.core.designsystem.StyleChoice

/** Razmaci na mreži od 4/8 dp. Kompaktna gustina smanjuje razmake, ali ne i dodirne površine. */
@Immutable
data class Spacing(
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    /** Bočna margina ekrana. */
    val gutter: Dp,
    /** Najmanja dodirna površina (pristupačnost). */
    val minTouch: Dp = 48.dp,
)

fun spacingFor(density: Density, style: StyleChoice): Spacing {
    val airy = density == Density.Airy
    val generous = style == StyleChoice.LuxuryMinimal || style == StyleChoice.Editorial
    return Spacing(
        xxs = 4.dp,
        xs = if (airy) 8.dp else 4.dp,
        sm = if (airy) 12.dp else 8.dp,
        md = if (airy) 16.dp else 12.dp,
        lg = if (airy) 24.dp else 16.dp,
        xl = if (airy) (if (generous) 40.dp else 32.dp) else 24.dp,
        xxl = if (airy) (if (generous) 56.dp else 48.dp) else 32.dp,
        gutter = if (airy) (if (generous) 24.dp else 20.dp) else 16.dp,
    )
}

/** Zaobljenja po izboru (Oštro 4–8, Umereno 12–20, Jako 24–32) i stilu. */
@Immutable
data class Radii(
    val small: Dp,
    val medium: Dp,
    val card: Dp,
    val sheet: Dp,
    /** Dugmad u obliku pilule kod jakog zaobljenja. */
    val pillButtons: Boolean,
) {
    val smallShape get() = RoundedCornerShape(small)
    val mediumShape get() = RoundedCornerShape(medium)
    val cardShape get() = RoundedCornerShape(card)
    val sheetShape get() = RoundedCornerShape(topStart = sheet, topEnd = sheet)
    val buttonShape get() = if (pillButtons) RoundedCornerShape(percent = 50) else RoundedCornerShape(medium)
}

fun radiiFor(roundness: Roundness, style: StyleChoice): Radii {
    val base = when (roundness) {
        Roundness.Sharp -> Radii(small = 4.dp, medium = 6.dp, card = 8.dp, sheet = 12.dp, pillButtons = false)
        Roundness.Moderate -> Radii(small = 8.dp, medium = 12.dp, card = 20.dp, sheet = 28.dp, pillButtons = false)
        Roundness.Round -> Radii(small = 16.dp, medium = 24.dp, card = 28.dp, sheet = 32.dp, pillButtons = true)
    }
    return when (style) {
        StyleChoice.ModernClean -> base.copy(card = base.card - 4.dp, medium = base.medium - 2.dp)
        StyleChoice.SoftPastel -> base.copy(card = base.card + 4.dp, medium = base.medium + 4.dp, pillButtons = true)
        StyleChoice.Editorial -> base.copy(card = (base.card - 8.dp).coerceAtLeast(2.dp))
        else -> base
    }
}

fun Radii.toMaterialShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(small / 2),
    small = RoundedCornerShape(small),
    medium = RoundedCornerShape(medium),
    large = RoundedCornerShape(card),
    extraLarge = RoundedCornerShape(sheet),
)

/** Senke, ivice i obrada fotografija — po stilu. */
@Immutable
data class Surfaces(
    val cardElevation: Dp,
    val raisedElevation: Dp,
    val cardBorder: Dp,
    /** Tamni gradijent preko donjeg dela fotografije (za čitljiv tekst). */
    val photoScrim: Float,
    /** Desaturacija fotografija (0 = bez, 1 = crno-belo). */
    val photoDesaturation: Float,
    val photoAspect: Float,
    /** Stil „Staklo“: zamućene površine (samo Android 12+ i bez uštede baterije). */
    val glass: Boolean,
)

fun surfacesFor(style: StyleChoice): Surfaces = when (style) {
    StyleChoice.LuxuryMinimal -> Surfaces(2.dp, 6.dp, 0.dp, photoScrim = 0.35f, photoDesaturation = 0f, photoAspect = 16f / 10f, glass = false)
    StyleChoice.ModernClean -> Surfaces(0.dp, 2.dp, 1.dp, photoScrim = 0.25f, photoDesaturation = 0f, photoAspect = 16f / 9f, glass = false)
    StyleChoice.Editorial -> Surfaces(0.dp, 4.dp, 0.dp, photoScrim = 0.55f, photoDesaturation = 0f, photoAspect = 4f / 5f, glass = false)
    StyleChoice.DarkNoir -> Surfaces(8.dp, 12.dp, 0.dp, photoScrim = 0.6f, photoDesaturation = 0.35f, photoAspect = 3f / 2f, glass = false)
    StyleChoice.SoftPastel -> Surfaces(3.dp, 8.dp, 0.dp, photoScrim = 0.2f, photoDesaturation = 0f, photoAspect = 3f / 2f, glass = false)
    StyleChoice.Glass -> Surfaces(0.dp, 0.dp, 1.dp, photoScrim = 0.3f, photoDesaturation = 0f, photoAspect = 16f / 10f, glass = true)
}

/** Pokret: spring animacije 200–350 ms; smanjene animacije ih isključuju. */
@Immutable
data class Motion(
    val enabled: Boolean,
    val durationMs: Int,
    val dampingRatio: Float,
    val stiffness: Float,
    /** Paralaksa, zamućenje i shared element prelazi. */
    val richEffects: Boolean,
    val staggerMs: Int,
) {
    fun <T> spec(): FiniteAnimationSpec<T> =
        if (!enabled) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> tweenSpec(): FiniteAnimationSpec<T> = if (!enabled) snap() else tween(durationMs)

    fun offsetSpec(): FiniteAnimationSpec<IntOffset> =
        if (!enabled) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

fun motionFor(level: MotionLevel, reduceMotion: Boolean, lowPower: Boolean): Motion {
    if (reduceMotion) return Motion(false, 0, 1f, Spring.StiffnessHigh, richEffects = false, staggerMs = 0)
    return when (level) {
        MotionLevel.Minimal -> Motion(true, 200, Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, richEffects = false, staggerMs = 0)
        MotionLevel.Subtle -> Motion(true, 280, 0.9f, Spring.StiffnessMediumLow, richEffects = !lowPower, staggerMs = 30)
        MotionLevel.Expressive -> Motion(true, 350, 0.72f, Spring.StiffnessLow, richEffects = !lowPower, staggerMs = 50)
    }
}
