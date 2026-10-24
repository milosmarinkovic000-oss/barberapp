package rs.termin.app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage

/** Skeleton umesto spinnera: blagi talas preko oblika sadržaja (bez animacije kad je pokret smanjen). */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = TerminTheme.radii.smallShape) {
    val colors = TerminTheme.colors
    val motion = TerminTheme.motion
    val brushModifier = if (motion.enabled) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "skeleton-progress",
        )
        Modifier.drawWithContent {
            val width = size.width
            val x = -width + progress * width * 3
            drawRect(
                Brush.linearGradient(
                    listOf(colors.skeleton, colors.skeletonHighlight, colors.skeleton),
                    start = Offset(x, 0f),
                    end = Offset(x + width, size.height),
                ),
            )
        }
    } else {
        Modifier.background(colors.skeleton)
    }
    Box(modifier.clip(shape).then(brushModifier).clearAndSetSemantics { })
}

/** Skeleton nekoliko redova teksta. */
@Composable
fun SkeletonLines(lines: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(lines) { i ->
            SkeletonBox(
                Modifier
                    .fillMaxWidth(if (i == lines - 1) 0.6f else 1f)
                    .height(14.dp),
            )
        }
    }
}

/** Ekran se učitava: najavljuje se TalkBack-u, a vizuelno su skeleton oblici. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val label = stringResource(R.string.state_loading)
    Box(modifier.semantics { contentDescription = label; liveRegion = LiveRegionMode.Polite }) { content() }
}

/** Prazno stanje: ilustracija, poruka i akcija. */
@Composable
fun EmptyState(
    illustration: Illustration,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = TerminTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        IllustrationImage(illustration, Modifier.size(140.dp))
        Spacer(Modifier.height(spacing.xs))
        Text(
            title,
            style = TerminTheme.type.title2,
            color = TerminTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(message, style = TerminTheme.type.body, color = TerminTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(spacing.xs))
            TerminButton(actionLabel, onAction, variant = ButtonVariant.Primary)
        }
    }
}

/** Greška sa dugmetom „Pokušaj ponovo“. */
@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_error_title),
    message: String = stringResource(R.string.state_error_message),
) {
    EmptyState(
        illustration = Illustration.NoConnection,
        title = title,
        message = message,
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Traka „bez interneta“: keširani podaci se prikazuju, a radnje koje traže mrežu su objašnjene. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier, message: String = stringResource(R.string.state_offline)) {
    val colors = TerminTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.warningSoft)
            .padding(horizontal = TerminTheme.spacing.gutter, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TerminIconImage(TerminIcon.WifiOff, contentDescription = null, tint = colors.onWarningSoft, size = 20.dp)
        Text(message, style = TerminTheme.type.callout, color = colors.onWarningSoft, modifier = Modifier.weight(1f))
    }
}

/** Obaveštenje u liniji (info, upozorenje, greška, uspeh). */
enum class NoticeTone { Info, Warning, Error, Success }

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.Info, icon: TerminIcon? = null) {
    val colors = TerminTheme.colors
    val (bg, fg) = when (tone) {
        NoticeTone.Info -> colors.accentSoft to colors.onAccentSoft
        NoticeTone.Warning -> colors.warningSoft to colors.onWarningSoft
        NoticeTone.Error -> colors.errorSoft to colors.onErrorSoft
        NoticeTone.Success -> colors.successSoft to colors.onSuccessSoft
    }
    val defaultIcon = when (tone) {
        NoticeTone.Info -> TerminIcon.Info
        NoticeTone.Warning -> TerminIcon.Warning
        NoticeTone.Error -> TerminIcon.Error
        NoticeTone.Success -> TerminIcon.CheckCircle
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TerminTheme.radii.mediumShape)
            .background(bg)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TerminIconImage(icon ?: defaultIcon, contentDescription = null, tint = fg, size = 20.dp)
        Text(text, style = TerminTheme.type.callout, color = fg, modifier = Modifier.weight(1f))
    }
}

@Composable
fun VerticalSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HorizontalSpace(width: Dp) = Spacer(Modifier.width(width))
