package rs.termin.app.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage

/** Gornja traka dizajn sistema. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    transparent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TerminTheme.colors
    TopAppBar(
        title = {
            Text(
                title,
                style = TerminTheme.type.title3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                TerminIconButton(TerminIcon.ArrowBack, stringResource(R.string.cd_back), onBack)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (transparent) Color.Transparent else colors.background,
            scrolledContainerColor = colors.surface,
            titleContentColor = colors.text,
            navigationIconContentColor = colors.text,
            actionIconContentColor = colors.text,
        ),
    )
}

/** Nadnaslov (overline): velika slova i razmak između slova po stilu. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = TerminTheme.colors.textSecondary) {
    val type = TerminTheme.type
    Text(
        text = if (type.uppercaseOverline) text.uppercase() else text,
        style = type.overline,
        color = color,
        modifier = modifier,
    )
}

/** Zaglavlje sekcije sa opcionom akcijom („Vidi sve“). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = TerminTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TerminTheme.type.title2, color = TerminTheme.colors.text, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
        }
        if (actionLabel != null && onAction != null) {
            TerminButton(actionLabel, onAction, variant = ButtonVariant.Text)
        }
    }
}

/** Stavka liste: ikonica, naslov, podnaslov i sadržaj desno. Najmanje 56 dp visine. */
@Composable
fun TerminListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: TerminIcon? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: Color = TerminTheme.colors.text,
    onClick: (() -> Unit)? = null,
) {
    val colors = TerminTheme.colors
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = TerminTheme.spacing.gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                leading != null -> leading()
                leadingIcon != null -> TerminIconImage(leadingIcon, contentDescription = null, tint = colors.textSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = TerminTheme.type.bodyStrong, color = titleColor)
                if (subtitle != null) Text(subtitle, style = TerminTheme.type.callout, color = colors.textSecondary)
            }
            trailing?.invoke()
            if (showChevron) TerminIconImage(TerminIcon.ChevronRight, contentDescription = null, tint = colors.textSecondary)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, color = Color.Transparent, modifier = modifier) { content() }
    } else {
        Box(modifier) { content() }
    }
}

@Composable
fun TerminDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = TerminTheme.colors.divider)
}

/** Prikaz ocene: zvezdice (dekorativno) + tekst; TalkBack čita „Ocena 4,8 od 5, 12 recenzija“. */
@Composable
fun RatingSummary(average: Double, count: Int, averageText: String, countText: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = TerminTheme.colors
    val description = if (count == 0) stringResource(R.string.cd_rating_no_reviews) else stringResource(R.string.cd_rating, averageText, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TerminIconImage(TerminIcon.StarFilled, contentDescription = null, tint = colors.accentText, size = 18.dp)
        if (count > 0) {
            Text(averageText, style = TerminTheme.type.bodyStrong, color = colors.text)
            Text(if (compact) "($count)" else countText, style = TerminTheme.type.callout, color = colors.textSecondary)
        }
    }
}

/** Unos ocene 1–5; svaka zvezdica je dodirna površina od 48 dp. */
@Composable
fun RatingInput(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            val label = stringResource(R.string.cd_star_input, i)
            Surface(
                onClick = { onChange(i) },
                color = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .semantics {
                        contentDescription = label
                        selected = i == value
                        role = Role.RadioButton
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TerminIconImage(
                        if (i <= value) TerminIcon.StarFilled else TerminIcon.Star,
                        contentDescription = null,
                        tint = if (i <= value) colors.accentText else colors.outline,
                        size = 36.dp,
                    )
                }
            }
        }
    }
}

/** Donji sheet (zaobljenje iz dizajn sistema, poštuje navigacionu traku i tastaturu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminBottomSheet(onDismiss: () -> Unit, skipPartiallyExpanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = TerminTheme.radii.sheetShape,
        containerColor = TerminTheme.colors.surface,
        contentColor = TerminTheme.colors.text,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), content = content)
    }
}

/** Dijalog za potvrdu radnje. */
@Composable
fun TerminDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = TerminTheme.type.title3) },
        text = { Text(text, style = TerminTheme.type.body) },
        confirmButton = {
            TerminButton(confirmLabel, onConfirm, variant = if (destructive) ButtonVariant.Danger else ButtonVariant.Primary)
        },
        dismissButton = { TerminButton(dismissLabel, onDismiss, variant = ButtonVariant.Text) },
        containerColor = TerminTheme.colors.surface,
        shape = TerminTheme.radii.cardShape,
    )
}

/** Traka napretka toka zakazivanja („Korak 2 od 4“). */
@Composable
fun StepProgress(current: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    val motion = TerminTheme.motion
    val progress by animateFloatAsState((current + 1f) / total, animationSpec = motion.spec(), label = "step-progress")
    Column(
        modifier.semantics {
            stateDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(current + 1f, 1f..total.toFloat(), total - 2)
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.accentText),
            )
        }
    }
}

/** Monogram logo (prvo slovo naziva) — ista grafika kao ikonica aplikacije. */
@Composable
fun Monogram(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val colors = TerminTheme.colors
    val description = stringResource(R.string.cd_logo)
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val s = this.size.minDimension
        drawCircle(colors.accent, radius = s / 2)
        drawCircle(colors.onAccent.copy(alpha = 0.25f), radius = s * 0.42f, style = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.012f))
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(colors.onAccent, Offset(s * 0.32f, s * 0.33f), Offset(s * 0.68f, s * 0.33f), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
        val stem = Path().apply {
            moveTo(s * 0.5f, s * 0.33f)
            lineTo(s * 0.5f, s * 0.7f)
        }
        drawPath(stem, colors.onAccent, style = stroke)
    }
}

/** Ograničava širinu sadržaja na velikim ekranima (čitljivost na tabletu). */
fun Modifier.readableWidth(max: Dp = 720.dp): Modifier = this.widthIn(max = max)
