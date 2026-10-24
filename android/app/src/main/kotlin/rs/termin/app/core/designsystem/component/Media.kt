package rs.termin.app.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.color.ColorMath

/**
 * Fotografija sa obradom po stilu: desaturacija (noir), tamni gradijent za čitljiv tekst
 * i, za stil „Staklo“ na Android-u 12+, zamućena traka ispod sadržaja [footer].
 * Coil učitava sliku u veličini prikaza, sa kešom u memoriji i na disku.
 */
@Composable
fun TerminPhoto(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scrim: Boolean = false,
    footerHeight: Dp = 0.dp,
    placeholderLabel: String? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null,
) {
    val surfaces = TerminTheme.surfaces
    val colors = TerminTheme.colors
    val context = LocalContext.current
    val motion = TerminTheme.motion
    val request = remember(url, motion.enabled) {
        ImageRequest.Builder(context).data(url).crossfade(motion.enabled).build()
    }
    val filter = remember(surfaces.photoDesaturation) {
        if (surfaces.photoDesaturation > 0f) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - surfaces.photoDesaturation) })
        } else {
            null
        }
    }
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    BoxWithConstraints(modifier.background(colors.surfaceMuted).then(described)) {
        val fullHeight = maxHeight
        if (url == null) {
            PhotoPlaceholder(placeholderLabel)
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (scrim && surfaces.photoScrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = surfaces.photoScrim + 0.15f),
                        ),
                    ),
            )
        }
        if (footer != null) {
            val glassBlur = TerminTheme.useBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && url != null
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(if (footerHeight > 0.dp) Modifier.requiredHeight(footerHeight) else Modifier)
                    .clipToBounds(),
            ) {
                if (glassBlur && footerHeight > 0.dp) {
                    // Ista fotografija, poravnata uz donju ivicu i zamućena: efekat stakla.
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = filter,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .requiredHeight(fullHeight)
                            .blur(20.dp),
                    )
                    Box(Modifier.matchParentSize().background(colors.glass.copy(alpha = 0.35f)))
                } else if (TerminTheme.surfaces.glass) {
                    Box(Modifier.matchParentSize().background(colors.glass))
                }
                footer()
            }
        }
    }
}

@Composable
private fun BoxScope.PhotoPlaceholder(label: String?) {
    val colors = TerminTheme.colors
    Box(
        Modifier
            .matchParentSize()
            .background(Brush.linearGradient(listOf(colors.accentSoft, colors.surfaceMuted))),
        contentAlignment = Alignment.Center,
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label.take(1).uppercase(),
                style = TerminTheme.type.display,
                color = colors.onAccentSoft,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** Avatar sa fotografijom ili inicijalima (kontrast inicijala proveren). */
@Composable
fun Avatar(url: String?, name: String, modifier: Modifier = Modifier, size: Dp = 48.dp, tint: Color? = null) {
    val colors = TerminTheme.colors
    val background = tint ?: colors.accentSoft
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials,
                color = ColorMath.bestOn(background),
                fontWeight = FontWeight.SemiBold,
                // Veličina u dp (ne raste sa fontom), da inicijali uvek stanu u krug.
                fontSize = with(LocalDensity.current) { (size * 0.36f).toSp() },
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
