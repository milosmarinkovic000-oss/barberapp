package rs.termin.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.ThemeMode
import rs.termin.app.core.designsystem.color.ColorMath
import rs.termin.app.core.designsystem.color.accentShades
import rs.termin.app.core.designsystem.color.paletteSpec
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SegmentedControl
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val preferences: PreferencesRepository) : ViewModel() {
    val appearance: StateFlow<Appearance?> = preferences.preferences.map { it.appearance }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setAccentShade(index: Int?) = update { it.copy(accentShade = index) }

    private fun update(change: (Appearance) -> Appearance) {
        val current = appearance.value ?: return
        viewModelScope.launch { preferences.setAppearance(change(current)) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

/** Podešavanja → Izgled: tema i nijansa akcenta; promena se odmah vidi u pregledu i celoj aplikaciji. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val config = TerminTheme.config
    val dark = TerminTheme.inputs.dark
    val spec = paletteSpec(config.palette, config.customAccent)
    val shades = accentShades(if (dark) spec.darkAccent else spec.lightAccent)
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.appearance_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val current = appearance ?: return@Column
            Column(
                Modifier
                    .readableWidth()
                    .fillMaxWidth()
                    .padding(spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Overline(stringResource(R.string.appearance_theme))
                    SegmentedControl(
                        options = ThemeMode.entries,
                        selected = current.themeMode,
                        label = { mode ->
                            when (mode) {
                                ThemeMode.System -> stringResource(R.string.appearance_theme_system)
                                ThemeMode.Light -> stringResource(R.string.appearance_theme_light)
                                ThemeMode.Dark -> stringResource(R.string.appearance_theme_dark)
                            }
                        },
                        onSelect = viewModel::setThemeMode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (config.style == StyleChoice.DarkNoir) Notice(stringResource(R.string.appearance_noir_note))
                }
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Overline(stringResource(R.string.appearance_accent))
                    FlowRow(
                        Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShadeSwatch(
                            color = if (dark) spec.darkAccent else spec.lightAccent,
                            label = stringResource(R.string.appearance_accent_default),
                            selected = current.accentShade == null,
                            onClick = { viewModel.setAccentShade(null) },
                        )
                        shades.forEachIndexed { index, color ->
                            ShadeSwatch(
                                color = color,
                                label = stringResource(R.string.appearance_accent_option, index + 1),
                                selected = current.accentShade == index,
                                onClick = { viewModel.setAccentShade(index) },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Overline(stringResource(R.string.appearance_preview))
                    PreviewCard()
                }
            }
        }
    }
}

@Composable
private fun ShadeSwatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TerminTheme.colors
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.text else colors.outline), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) TerminIconImage(TerminIcon.Check, contentDescription = null, tint = ColorMath.bestOn(color))
    }
}

/** Primer kartice u trenutnoj temi: pokazuje kako izgledaju tekst, akcenat i dugme. */
@Composable
private fun PreviewCard() {
    val colors = TerminTheme.colors
    TerminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(null, stringResource(R.string.appearance_preview_salon), size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_preview_salon), style = TerminTheme.type.title3, color = colors.text)
                    Text(stringResource(R.string.appearance_preview_text), style = TerminTheme.type.callout, color = colors.textSecondary)
                }
                TerminIconImage(TerminIcon.FavoriteFilled, contentDescription = null, tint = colors.accentText)
            }
            // Prikaz izgleda primarnog dugmeta; namerno nije interaktivan.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(TerminTheme.radii.buttonShape)
                    .background(colors.accent)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.appearance_preview_button), style = TerminTheme.type.button, color = colors.onAccent)
            }
        }
    }
}
