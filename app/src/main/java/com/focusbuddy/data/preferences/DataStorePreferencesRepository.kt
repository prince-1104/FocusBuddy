package com.focusbuddy.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.focusbuddy.domain.model.*
import com.focusbuddy.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.map

/** One DataStore instance per file/process; the application container owns the repository. */
internal val Context.focusBuddyPreferences by preferencesDataStore(
    name = "settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "appearance", setOf("theme_mode"))) },
)

class DataStorePreferencesRepository(private val store: DataStore<Preferences>) : PreferencesRepository {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val mascot = stringPreferencesKey("selected_mascot")
        val personality = stringPreferencesKey("personality")
        val colors = stringSetPreferencesKey("supported_colors")
        val selectedColor = longPreferencesKey("selected_color")
        val animations = booleanPreferencesKey("animations_enabled")
        val sound = booleanPreferencesKey("mascot_sound_enabled")
    }

    // Read failures propagate to the caller; personal preferences are never silently erased.
    override val settings = store.data.map { preferences ->
        val defaults = MascotSettings()
        val colors = preferences[Keys.colors]?.mapNotNull { it.toLongOrNull() }
            ?.filter { it in 0..0xFFFFFFFFL }?.toSet()?.takeIf { it.isNotEmpty() } ?: defaults.supportedColors
        MascotSettings(
            selectedMascot = preferences[Keys.mascot]?.takeIf { it.isNotBlank() } ?: defaults.selectedMascot,
            personality = MascotPersonality.entries.firstOrNull { it.name == preferences[Keys.personality] } ?: defaults.personality,
            supportedColors = colors,
            selectedColor = preferences[Keys.selectedColor]?.takeIf { it in colors } ?: colors.min(),
            theme = ThemeMode.entries.firstOrNull { it.name == preferences[Keys.theme] } ?: defaults.theme,
            animationsEnabled = preferences[Keys.animations] ?: defaults.animationsEnabled,
            soundEnabled = preferences[Keys.sound] ?: defaults.soundEnabled,
        )
    }

    override suspend fun setTheme(mode: ThemeMode) { store.edit { it[Keys.theme] = mode.name } }

    override suspend fun setSettings(settings: MascotSettings) {
        store.edit {
            it[Keys.theme] = settings.theme.name
            it[Keys.mascot] = settings.selectedMascot
            it[Keys.personality] = settings.personality.name
            it[Keys.colors] = settings.supportedColors.map(Long::toString).toSet()
            it[Keys.selectedColor] = settings.selectedColor
            it[Keys.animations] = settings.animationsEnabled
            it[Keys.sound] = settings.soundEnabled
        }
    }
}
