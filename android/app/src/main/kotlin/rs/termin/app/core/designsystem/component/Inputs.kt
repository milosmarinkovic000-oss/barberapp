package rs.termin.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage

/** Tekstualno polje dizajn sistema (oznaka iznad, greška i pomoćni tekst ispod). */
@Composable
fun TerminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: TerminIcon? = null,
    trailing: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = TerminTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.textSecondary) } },
        leadingIcon = leadingIcon?.let { { TerminIconImage(it, contentDescription = null, tint = colors.textSecondary) } },
        trailingIcon = trailing,
        prefix = prefix?.let { { Text(it, style = TerminTheme.type.body, color = colors.textSecondary) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = TerminTheme.type.body,
        shape = TerminTheme.radii.mediumShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentText,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.accentText,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.accentText,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorBorderColor = colors.error,
            errorLabelColor = colors.error,
            errorSupportingTextColor = colors.error,
            unfocusedSupportingTextColor = colors.textSecondary,
        ),
    )
}

/** Chip za izbor (kategorije, filteri, dani). Izabrano stanje se najavljuje TalkBack-u. */
@Composable
fun TerminChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: TerminIcon? = null,
    enabled: Boolean = true,
) {
    val colors = TerminTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = TerminTheme.radii.buttonShape,
        color = if (selected) colors.accent else colors.surface,
        contentColor = if (selected) colors.onAccent else colors.text,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                TerminIconImage(icon, contentDescription = null, size = 18.dp, tint = if (selected) colors.onAccent else colors.textSecondary)
            }
            Text(label, style = TerminTheme.type.callout)
        }
    }
}

/** Prekidač sa oznakom; cela vrsta je dodirna (48 dp) i čita se kao prekidač. */
@Composable
fun TerminSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = TerminTheme.colors
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .semantics { role = Role.Switch },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TerminTheme.spacing.gutter, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(title, style = TerminTheme.type.bodyStrong, color = colors.text)
                if (subtitle != null) Text(subtitle, style = TerminTheme.type.callout, color = colors.textSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accentText,
                    checkedThumbColor = colors.surface,
                    uncheckedTrackColor = colors.surfaceMuted,
                    uncheckedBorderColor = colors.outline,
                    uncheckedThumbColor = colors.outline,
                ),
            )
        }
    }
}
