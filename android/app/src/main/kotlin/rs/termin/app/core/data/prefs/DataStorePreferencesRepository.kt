package rs.termin.app.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import rs.termin.app.BuildConfig
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.designsystem.ThemeMode
import rs.termin.app.core.domain.repository.AppLanguage
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.PreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "termin_prefs")

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PreferencesRepository {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val themeMode = stringPreferencesKey("theme_mode")
        val accentShade = intPreferencesKey("accent_shade")
        val language = stringPreferencesKey("language")
        val notificationAsked = booleanPreferencesKey("notification_permission_asked")
        val firstBooking = booleanPreferencesKey("first_booking_done")
        val recentSearches = stringPreferencesKey("recent_searches")
        val debugTheme = stringPreferencesKey("debug_theme_config")
    }

    override val preferences: Flow<AppPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppPreferences(
                onboardingDone = p[Keys.onboarding] == true,
                appearance = Appearance(
                    themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                        ?: ThemeConfig.Default.initialThemeMode,
                    accentShade = p[Keys.accentShade]?.takeIf { it >= 0 },
                ),
                language = p[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.System,
                notificationPermissionAsked = p[Keys.notificationAsked] == true,
                firstBookingDone = p[Keys.firstBooking] == true,
                recentSearches = p[Keys.recentSearches]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
                // U release buildu važi isključivo izbor iz ThemeConfig.Default.
                debugThemeConfig = if (BuildConfig.DEBUG) p[Keys.debugTheme]?.let(ThemeConfigCodec::decode) else null,
            )
        }

    override suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.onboarding] = true }
    }

    override suspend fun setAppearance(appearance: Appearance) {
        context.dataStore.edit {
            it[Keys.themeMode] = appearance.themeMode.name
            it[Keys.accentShade] = appearance.accentShade ?: -1
        }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.language] = language.name }
    }

    override suspend fun setNotificationPermissionAsked() {
        context.dataStore.edit { it[Keys.notificationAsked] = true }
    }

    override suspend fun setFirstBookingDone() {
        context.dataStore.edit { it[Keys.firstBooking] = true }
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        context.dataStore.edit { p ->
            val current = p[Keys.recentSearches]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            p[Keys.recentSearches] = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_RECENT)
                .joinToString("\n")
        }
    }

    override suspend fun setDebugThemeConfig(config: ThemeConfig?) {
        if (!BuildConfig.DEBUG) return
        context.dataStore.edit { p ->
            if (config == null) p.remove(Keys.debugTheme) else p[Keys.debugTheme] = ThemeConfigCodec.encode(config)
        }
    }

    private companion object {
        const val MAX_RECENT = 6
    }
}
