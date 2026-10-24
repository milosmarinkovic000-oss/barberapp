package rs.termin.app.feature.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
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
import rs.termin.app.core.designsystem.Density
import rs.termin.app.core.designsystem.Features
import rs.termin.app.core.designsystem.IconStyle
import rs.termin.app.core.designsystem.MotionLevel
import rs.termin.app.core.designsystem.NavigationStyle
import rs.termin.app.core.designsystem.PaletteChoice
import rs.termin.app.core.designsystem.Roundness
import rs.termin.app.core.designsystem.SalonCardStyle
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.designsystem.ThemeMode
import rs.termin.app.core.designsystem.TypographyChoice
import rs.termin.app.core.designsystem.color.ColorMath
import rs.termin.app.core.designsystem.color.SalonAccentOptions
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminSwitchRow
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.feature.profile.FormScaffold
import javax.inject.Inject

/** Samo debug: izbor iz odeljka 0 koji se menja uživo i pamti u DataStore-u. */
@HiltViewModel
class StylePreviewViewModel @Inject constructor(private val preferences: PreferencesRepository) : ViewModel() {
    val config: StateFlow<ThemeConfig> = preferences.preferences.map { it.debugThemeConfig ?: ThemeConfig.Default }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ThemeConfig.Default)

    fun update(change: (ThemeConfig) -> ThemeConfig) {
        val next = change(config.value)
        viewModelScope.launch { preferences.setDebugThemeConfig(next) }
    }

    fun features(change: (Features) -> Features) = update { it.copy(features = change(it.features)) }

    fun reset() {
        viewModelScope.launch { preferences.setDebugThemeConfig(null) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

/** „LuxuryMinimal“ → „Luxury minimal“ (nazivi su iz koda; ekran je samo za razvoj). */
private fun humanize(name: String): String =
    name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar(Char::uppercase)

private val StyleLetters = mapOf(
    StyleChoice.LuxuryMinimal to "A",
    StyleChoice.ModernClean to "B",
    StyleChoice.Editorial to "C",
    StyleChoice.DarkNoir to "D",
    StyleChoice.SoftPastel to "E",
    StyleChoice.Glass to "F",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Enum<T>> ChoiceGroup(title: String, options: List<T>, selected: T, label: (T) -> String = { humanize(it.name) }, onSelect: (T) -> Unit) {
    Overline(title)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> TerminChip(label(option), selected = option == selected, onClick = { onSelect(option) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylePreviewScreen(onBack: () -> Unit, viewModel: StylePreviewViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val colors = TerminTheme.colors
    FormScaffold(stringResource(R.string.debug_style_title), onBack) {
        Notice(stringResource(R.string.debug_style_intro), icon = TerminIcon.Info)
        Specimen()
        ChoiceGroup(stringResource(R.string.debug_group_style), StyleChoice.entries, config.style, { "${StyleLetters[it]} · ${humanize(it.name)}" }) { s ->
            viewModel.update { it.copy(style = s) }
        }
        ChoiceGroup(stringResource(R.string.debug_group_palette), PaletteChoice.entries, config.palette) { p -> viewModel.update { it.copy(palette = p) } }
        if (config.palette == PaletteChoice.Custom) {
            Overline(stringResource(R.string.debug_group_custom))
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SalonAccentOptions.forEach { option ->
                    val rgb = option.toArgb().toLong() and RGB_MASK
                    val selected = rgb == config.customAccent
                    val hex = "#%06X".format(rgb)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.text else colors.outline), CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(option)
                            .selectable(selected, onClick = { viewModel.update { it.copy(customAccent = rgb) } }, role = Role.RadioButton)
                            .semantics { contentDescription = hex },
                    ) {
                        if (selected) TerminIconImage(TerminIcon.Check, contentDescription = null, tint = ColorMath.bestOn(option), modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
        ChoiceGroup(stringResource(R.string.debug_group_type), TypographyChoice.entries, config.typography) { t -> viewModel.update { it.copy(typography = t) } }
        ChoiceGroup(stringResource(R.string.debug_group_roundness), Roundness.entries, config.roundness) { r -> viewModel.update { it.copy(roundness = r) } }
        ChoiceGroup(stringResource(R.string.debug_group_density), Density.entries, config.density) { d -> viewModel.update { it.copy(density = d) } }
        ChoiceGroup(stringResource(R.string.debug_group_motion), MotionLevel.entries, config.motion) { m -> viewModel.update { it.copy(motion = m) } }
        ChoiceGroup(stringResource(R.string.debug_group_icons), IconStyle.entries, config.iconStyle) { i -> viewModel.update { it.copy(iconStyle = i) } }
        ChoiceGroup(stringResource(R.string.debug_group_card), SalonCardStyle.entries, config.salonCard) { c -> viewModel.update { it.copy(salonCard = c) } }
        ChoiceGroup(stringResource(R.string.debug_group_navigation), NavigationStyle.entries, config.navigation) { n -> viewModel.update { it.copy(navigation = n) } }
        ChoiceGroup(stringResource(R.string.debug_group_theme), ThemeMode.entries, config.initialThemeMode) { t -> viewModel.update { it.copy(initialThemeMode = t) } }

        Overline(stringResource(R.string.debug_group_features))
        val f = config.features
        val toggles: List<Pair<String, Pair<Boolean, (Boolean) -> Features>>> = listOf(
            "phoneLogin" to (f.phoneLogin to { v -> f.copy(phoneLogin = v) }),
            "googleLogin" to (f.googleLogin to { v -> f.copy(googleLogin = v) }),
            "map" to (f.map to { v -> f.copy(map = v) }),
            "waitlist" to (f.waitlist to { v -> f.copy(waitlist = v) }),
            "reviews" to (f.reviews to { v -> f.copy(reviews = v) }),
            "favorites" to (f.favorites to { v -> f.copy(favorites = v) }),
            "ownerStats" to (f.ownerStats to { v -> f.copy(ownerStats = v) }),
            "googleCalendar" to (f.googleCalendar to { v -> f.copy(googleCalendar = v) }),
            "calendly" to (f.calendly to { v -> f.copy(calendly = v) }),
            "calcom" to (f.calcom to { v -> f.copy(calcom = v) }),
            "english" to (f.english to { v -> f.copy(english = v) }),
            "inAppAppearance" to (f.inAppAppearance to { v -> f.copy(inAppAppearance = v) }),
            "salonBranding" to (f.salonBranding to { v -> f.copy(salonBranding = v) }),
        )
        TerminCard(Modifier.fillMaxWidth()) {
            toggles.forEachIndexed { index, (name, value) ->
                if (index > 0) TerminDivider()
                TerminSwitchRow(humanize(name), value.first, { checked -> viewModel.features { value.second(checked) } })
            }
        }
        TerminButton(stringResource(R.string.debug_reset), viewModel::reset, variant = ButtonVariant.Secondary, icon = TerminIcon.Undo, fullWidth = true)
    }
}

/** Uzorak tipografije, boja i komponenti u trenutnom stilu, sa izračunatim kontrastom. */
@Composable
private fun Specimen() {
    val colors = TerminTheme.colors
    val type = TerminTheme.type
    TerminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(stringResource(R.string.debug_specimen))
            Text(stringResource(R.string.debug_specimen_title), style = type.display, color = colors.text)
            Text(stringResource(R.string.debug_specimen_body), style = type.body, color = colors.text)
            Text(stringResource(R.string.debug_specimen_body), style = type.callout, color = colors.textSecondary)
            val textContrast = ColorMath.contrast(colors.text, colors.background)
            val accentContrast = ColorMath.contrast(colors.accent, colors.background)
            Text(
                stringResource(R.string.debug_contrast, "%.1f".format(textContrast), "%.1f".format(accentContrast)),
                style = type.caption,
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(colors.accent, colors.accentSoft, colors.success, colors.warning, colors.error, colors.surfaceMuted).forEach { swatch ->
                    Box(Modifier.size(28.dp).clip(TerminTheme.radii.smallShape).background(swatch).border(1.dp, colors.divider, TerminTheme.radii.smallShape))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TerminIconImage(TerminIcon.ContentCut, contentDescription = null, tint = colors.accentText)
                TerminIconImage(TerminIcon.CalendarMonth, contentDescription = null, tint = colors.accentText)
                TerminIconImage(TerminIcon.Favorite, contentDescription = null, tint = colors.accentText)
                TerminIconImage(TerminIcon.StarFilled, contentDescription = null, tint = colors.accentText)
            }
        }
    }
}

private const val RGB_MASK = 0xFFFFFFL
