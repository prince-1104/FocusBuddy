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
        val weekStart = intPreferencesKey("week_start")
        val theme = stringPreferencesKey("theme_mode")
        val mascot = stringPreferencesKey("selected_mascot")
        val personality = stringPreferencesKey("personality")
        val colors = stringSetPreferencesKey("supported_colors")
        val selectedColor = longPreferencesKey("selected_color")
        val animations = booleanPreferencesKey("animations_enabled")
        val sound = booleanPreferencesKey("mascot_sound_enabled")
        val visible = booleanPreferencesKey("show_mascot")
        val timeFormat = stringPreferencesKey("time_format")
        val offset = intPreferencesKey("default_reminder_offset")
        val alertSound = booleanPreferencesKey("default_alert_sound")
        val alertVibration = booleanPreferencesKey("default_alert_vibration")
    }

    // Read failures propagate to the caller; personal preferences are never silently erased.
    override val settings = store.data.map { preferences ->
        val defaults = MascotSettings()
        val colors = preferences[Keys.colors]?.mapNotNull { it.toLongOrNull() }
            ?.filter { it in 0..0xFFFFFFFFL }?.toSet()?.takeIf { it.isNotEmpty() } ?: defaults.supportedColors
        MascotSettings(
            selectedMascot = com.focusbuddy.domain.mascot.MascotVariant.from(preferences[Keys.mascot].orEmpty()).id,
            personality = when (preferences[Keys.personality]) {
                "Encouraging" -> MascotPersonality.Friendly
                "Quiet" -> MascotPersonality.Focused
                else -> MascotPersonality.entries.firstOrNull { it.name == preferences[Keys.personality] } ?: defaults.personality
            },
            supportedColors = colors,
            selectedColor = preferences[Keys.selectedColor]?.takeIf { it in colors }
                ?: defaults.selectedColor.takeIf { it in colors } ?: colors.min(),
            theme = ThemeMode.entries.firstOrNull { it.name == preferences[Keys.theme] } ?: defaults.theme,
            animationsEnabled = preferences[Keys.animations] ?: defaults.animationsEnabled,
            soundEnabled = preferences[Keys.sound] ?: defaults.soundEnabled,
            showMascot = preferences[Keys.visible] ?: defaults.showMascot,
            weekStart = preferences[Keys.weekStart]?.takeIf { it in 1..7 }?.let(java.time.DayOfWeek::of),
            timeFormat = TimeFormat.entries.firstOrNull { it.name == preferences[Keys.timeFormat] } ?: defaults.timeFormat,
            defaultReminderOffset = preferences[Keys.offset]?.takeIf { it in setOf(0, 5, 10, 30) },
            defaultAlertSound = preferences[Keys.alertSound] ?: true,
            defaultAlertVibration = preferences[Keys.alertVibration] ?: true,
        )
    }

    override suspend fun setWeekStart(day: java.time.DayOfWeek?) { store.edit { it[Keys.weekStart] = day?.value ?: 0 } }
    override suspend fun setTheme(mode: ThemeMode) { store.edit { it[Keys.theme] = mode.name } }
    override suspend fun setMascotVisible(visible: Boolean) { store.edit { it[Keys.visible] = visible } }
    override suspend fun setAnimationsEnabled(enabled: Boolean) { store.edit { it[Keys.animations] = enabled } }
    override suspend fun setProductivity(format: TimeFormat, offset: Int?, sound: Boolean, vibration: Boolean) {
        require(offset == null || offset in setOf(0, 5, 10, 30))
        store.edit {
            it[Keys.timeFormat] = format.name; it[Keys.offset] = offset ?: -1
            it[Keys.alertSound] = sound; it[Keys.alertVibration] = vibration
        }
    }
    override suspend fun reset() { store.edit { it.clear() } }

    override suspend fun setSettings(settings: MascotSettings) {
        writeSettings(settings, includeWeekStart = true)
    }
    override suspend fun setMascotSettings(settings: MascotSettings) {
        writeSettings(settings, includeWeekStart = false)
    }
    private suspend fun writeSettings(settings: MascotSettings, includeWeekStart: Boolean) {
        store.edit {
            if (includeWeekStart) it[Keys.weekStart] = settings.weekStart?.value ?: 0
            if (includeWeekStart) {
                it[Keys.timeFormat] = settings.timeFormat.name; it[Keys.offset] = settings.defaultReminderOffset ?: -1
                it[Keys.alertSound] = settings.defaultAlertSound; it[Keys.alertVibration] = settings.defaultAlertVibration
            }
            it[Keys.theme] = settings.theme.name
            it[Keys.mascot] = settings.selectedMascot
            it[Keys.personality] = settings.personality.name
            it[Keys.colors] = settings.supportedColors.map(Long::toString).toSet()
            it[Keys.selectedColor] = settings.selectedColor
            it[Keys.animations] = settings.animationsEnabled
            it[Keys.sound] = settings.soundEnabled
            it[Keys.visible] = settings.showMascot
        }
    }
}
