package rs.termin.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminListItem
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.component.readableWidth
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.ui.Actions

private data class PolicySection(val title: Int, val body: Int)

private val PolicySections = listOf(
    PolicySection(R.string.privacy_data_title, R.string.privacy_data_body),
    PolicySection(R.string.privacy_why_title, R.string.privacy_why_body),
    PolicySection(R.string.privacy_who_title, R.string.privacy_who_body),
    PolicySection(R.string.privacy_calendars_title, R.string.privacy_calendars_body),
    PolicySection(R.string.privacy_retention_title, R.string.privacy_retention_body),
    PolicySection(R.string.privacy_rights_title, R.string.privacy_rights_body),
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.privacy_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SelectionContainer {
                Column(
                    Modifier
                        .readableWidth()
                        .fillMaxWidth()
                        .padding(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text(stringResource(R.string.privacy_updated), style = TerminTheme.type.caption, color = colors.textSecondary)
                    Text(stringResource(R.string.privacy_intro), style = TerminTheme.type.body, color = colors.text)
                    PolicySections.forEach { section ->
                        Text(
                            stringResource(section.title),
                            style = TerminTheme.type.title3,
                            color = colors.text,
                            modifier = Modifier
                                .padding(top = spacing.sm)
                                .semantics { heading() },
                        )
                        Text(stringResource(section.body), style = TerminTheme.type.body, color = colors.text)
                    }
                }
            }
        }
    }
}

private data class FontLicense(val name: String, val asset: String, val license: String = "SIL Open Font License 1.1")

private val FontLicenses = listOf(
    FontLicense("Fraunces", "licenses/font-fraunces.txt"),
    FontLicense("Inter", "licenses/font-inter.txt"),
    FontLicense("Playfair Display", "licenses/font-playfair-display.txt"),
    FontLicense("Manrope", "licenses/font-manrope.txt"),
    FontLicense("DM Serif Display", "licenses/font-dm-serif-display.txt"),
    FontLicense("DM Sans", "licenses/font-dm-sans.txt"),
    FontLicense("Poppins", "licenses/font-poppins.txt"),
    FontLicense("Material Symbols", "licenses/material-symbols.txt", "Apache License 2.0"),
)

private data class Library(val name: String, val license: String, val url: String)

private val Libraries = listOf(
    Library("Android Jetpack (AndroidX, Compose, Navigation, WorkManager, DataStore)", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Library("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0", "https://kotlinlang.org"),
    Library("Dagger Hilt", "Apache License 2.0", "https://dagger.dev/hilt"),
    Library("Firebase Android SDK", "Apache License 2.0", "https://github.com/firebase/firebase-android-sdk"),
    Library("Google Play services", "Android Software Development Kit License", "https://developers.google.com/android/guides/overview"),
    Library("Maps Compose", "Apache License 2.0", "https://github.com/googlemaps/android-maps-compose"),
    Library("GeoFire for Android", "MIT License", "https://github.com/firebase/geofire-android"),
    Library("Coil", "Apache License 2.0", "https://github.com/coil-kt/coil"),
    Library("OkHttp", "Apache License 2.0", "https://github.com/square/okhttp"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val context = LocalContext.current
    var openFont by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = colors.background,
        topBar = { TerminTopBar(stringResource(R.string.licenses_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .readableWidth()
                    .fillMaxWidth()
                    .padding(spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(stringResource(R.string.licenses_intro), style = TerminTheme.type.body, color = colors.textSecondary)
                Overline(stringResource(R.string.licenses_fonts))
                TerminCard(Modifier.fillMaxWidth()) {
                    FontLicenses.forEachIndexed { index, font ->
                        if (index > 0) TerminDivider()
                        TerminListItem(font.name, subtitle = font.license, showChevron = true, onClick = { openFont = font.asset })
                    }
                }
                Overline(stringResource(R.string.licenses_libraries))
                TerminCard(Modifier.fillMaxWidth()) {
                    Libraries.forEachIndexed { index, library ->
                        if (index > 0) TerminDivider()
                        TerminListItem(
                            library.name,
                            subtitle = library.license,
                            trailing = { TerminIconImage(TerminIcon.OpenInNew, contentDescription = null, tint = colors.textSecondary) },
                            onClick = { Actions.openCustomTab(context, library.url) },
                        )
                    }
                }
            }
        }
    }
    openFont?.let { asset ->
        TerminBottomSheet(onDismiss = { openFont = null }) {
            LicenseText(asset)
        }
    }
}

@Composable
private fun LicenseText(asset: String) {
    val context = LocalContext.current
    var text by remember(asset) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset) {
        text = withContext(Dispatchers.IO) { context.assets.open(asset).bufferedReader().use { it.readText() } }
    }
    Column(
        Modifier
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TerminTheme.spacing.gutter),
    ) {
        val value = text
        if (value == null) {
            SkeletonLines(8)
        } else {
            SelectionContainer {
                Text(value, style = TerminTheme.type.caption, color = TerminTheme.colors.text)
            }
        }
    }
}
