package rs.termin.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rs.termin.app.core.designsystem.TerminTheme

/** Segmentirani izbor (npr. Dan / Nedelja / Mesec, Lista / Mapa). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TerminTheme.colors
    Surface(
        modifier = modifier.selectableGroup(),
        shape = TerminTheme.radii.buttonShape,
        color = colors.surfaceMuted,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = TerminTheme.radii.buttonShape,
                    color = if (isSelected) colors.surface else Color.Transparent,
                    contentColor = if (isSelected) colors.text else colors.textSecondary,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .semantics {
                            this.selected = isSelected
                            role = Role.Tab
                        },
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label(option), style = TerminTheme.type.callout, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Horizontalni niz chip-ova sa skrolovanjem. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TerminTheme.spacing.gutter)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
