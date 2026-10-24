package rs.termin.app.core.designsystem.icon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.IconStyle
import rs.termin.app.core.designsystem.TerminTheme
import java.util.concurrent.ConcurrentHashMap

private val cache = ConcurrentHashMap<Pair<TerminIcon, IconStyle>, ImageVector>()

/** Vektor ikonice u izabranom stilu (Outlined/Rounded/Sharp), keširan. */
fun TerminIcon.vector(style: IconStyle): ImageVector = cache.getOrPut(this to style) {
    val data = when (style) {
        IconStyle.Outlined -> outlined
        IconStyle.Rounded -> rounded
        IconStyle.Sharp -> sharp
    }
    ImageVector.Builder(
        name = "$name.$style",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        // Material Symbols koriste viewBox "0 -960 960 960".
        group(translationY = 960f) {
            addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black))
        }
    }.build()
}

/**
 * Ikonica iz dizajn sistema. [contentDescription] je null samo za dekorativne ikonice
 * (kada isti smisao već nosi tekst pored).
 */
@Composable
fun TerminIconImage(
    icon: TerminIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
) {
    val style = TerminTheme.config.iconStyle
    val vector = remember(icon, style) { icon.vector(style) }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) TerminTheme.colors.text else tint,
    )
}
