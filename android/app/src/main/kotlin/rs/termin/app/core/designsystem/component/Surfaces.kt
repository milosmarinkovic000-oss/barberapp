package rs.termin.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.TerminTheme

/**
 * Kartica dizajn sistema: senka, ivica i zaobljenje zavise od stila (A–F), ne od ekrana.
 * Klik je opcion; kartica sa klikom ima ripple i ulogu dugmeta za TalkBack.
 */
@Composable
fun TerminCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = TerminTheme.radii.cardShape,
    color: Color = TerminTheme.colors.surface,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaces = TerminTheme.surfaces
    val colors = TerminTheme.colors
    val glass = TerminTheme.config.style == StyleChoice.Glass
    val container = if (glass) colors.glass else color
    val border = if (surfaces.cardBorder > 0.dp) BorderStroke(surfaces.cardBorder, colors.divider) else null
    val elevation = if (elevated) surfaces.cardElevation else 0.dp
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = container,
            contentColor = colors.text,
            shadowElevation = elevation,
            border = border,
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = container,
            contentColor = colors.text,
            shadowElevation = elevation,
            border = border,
        ) { Column(content = content) }
    }
}

/**
 * Poluprovidna površina za stil „Staklo“ (zamućenje fotografije ispod radi [TerminPhoto]
 * na Android-u 12+; ovde je samo providna podloga sa finom ivicom).
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = TerminTheme.radii.cardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TerminTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(colors.glass)
            .border(1.dp, colors.surface.copy(alpha = 0.35f), shape),
        content = content,
    )
}

/** Blaga senka po stilu (za plutajuće elemente poput donje trake zakazivanja). */
fun Modifier.raised(elevation: Dp, shape: Shape): Modifier = if (elevation > 0.dp) shadow(elevation, shape, clip = false) else this
