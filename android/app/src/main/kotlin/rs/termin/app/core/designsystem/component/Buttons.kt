package rs.termin.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage

enum class ButtonVariant { Primary, Secondary, Tonal, Text, Danger }

/**
 * Dugme dizajn sistema. Visina je najmanje 48 dp, tekst se prelama umesto da se seče
 * (i na 200 % veličine fonta), a [loading] prikazuje indikator bez promene širine.
 */
@Composable
fun TerminButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: TerminIcon? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val colors = TerminTheme.colors
    val radii = TerminTheme.radii
    val sizeModifier = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .defaultMinSize(minHeight = 52.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val content: @Composable () -> Unit = {
        ButtonContent(text = text, icon = icon, loading = loading, color = Color.Unspecified)
    }
    val click = { if (!loading) onClick() }
    when (variant) {
        ButtonVariant.Primary, ButtonVariant.Danger -> {
            val container = if (variant == ButtonVariant.Danger) colors.error else colors.accent
            val onContainer = if (variant == ButtonVariant.Danger) colors.onError else colors.onAccent
            Button(
                onClick = click,
                modifier = sizeModifier,
                enabled = enabled,
                shape = radii.buttonShape,
                contentPadding = padding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = onContainer,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.textSecondary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            ) { content() }
        }
        ButtonVariant.Secondary -> OutlinedButton(
            onClick = click,
            modifier = sizeModifier,
            enabled = enabled,
            shape = radii.buttonShape,
            contentPadding = padding,
            border = BorderStroke(1.dp, if (enabled) colors.outline else colors.divider),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text, disabledContentColor = colors.textSecondary),
        ) { content() }
        ButtonVariant.Tonal -> Button(
            onClick = click,
            modifier = sizeModifier,
            enabled = enabled,
            shape = radii.buttonShape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentSoft,
                contentColor = colors.onAccentSoft,
                disabledContainerColor = colors.surfaceMuted,
                disabledContentColor = colors.textSecondary,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        ) { content() }
        ButtonVariant.Text -> TextButton(
            onClick = click,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            enabled = enabled,
            shape = radii.buttonShape,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.accentText, disabledContentColor = colors.textSecondary),
        ) { content() }
    }
}

@Composable
private fun ButtonContent(text: String, icon: TerminIcon?, loading: Boolean, color: Color) {
    AnimatedContent(targetState = loading, label = "button-loading") { isLoading ->
        if (isLoading) {
            Box(Modifier.padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.material3.LocalContentColor.current)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) TerminIconImage(icon, contentDescription = null, size = 20.dp, tint = androidx.compose.material3.LocalContentColor.current)
                Text(
                    text = text,
                    style = TerminTheme.type.button,
                    color = color,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Dugme sa ikonicom; dodirna površina 48 dp, obavezan opis za TalkBack. */
@Composable
fun TerminIconButton(
    icon: TerminIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    selected: Boolean? = null,
) {
    val selectedLabel = selected?.let {
        if (it) androidx.compose.ui.res.stringResource(rs.termin.app.R.string.cd_selected)
        else androidx.compose.ui.res.stringResource(rs.termin.app.R.string.cd_not_selected)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .then(if (selectedLabel != null) Modifier.semantics { stateDescription = selectedLabel } else Modifier),
    ) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge(containerColor = TerminTheme.colors.error) { Text(if (badgeCount > 9) "9+" else badgeCount.toString()) } }) {
                TerminIconImage(icon, contentDescription, tint = tint)
            }
        } else {
            TerminIconImage(icon, contentDescription, tint = tint)
        }
    }
}
