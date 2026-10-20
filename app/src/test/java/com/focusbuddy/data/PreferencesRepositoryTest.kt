package com.focusbuddy.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.preferences.DataStorePreferencesRepository
import com.focusbuddy.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
class PreferencesRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "settings-${UUID.randomUUID()}.preferences_pb")
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun newRepository(legacyName: String? = null) = DataStorePreferencesRepository(
        PreferenceDataStoreFactory.create(
            migrations = legacyName?.let { listOf(SharedPreferencesMigration(context, it, setOf("theme_mode"))) } ?: emptyList(),
            scope = scope, produceFile = { file },
        ),
    )

    @After fun cleanup() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        file.delete()
    }

    @Test fun defaultsAreConfigurationNotSeededApplicationRecords() = runTest {
        assertEquals(MascotSettings(), newRepository().settings.first())
    }
    @Test fun productivityPreferencesSurviveReopenAndStudioSavesAndResetClearsThem() = runTest {
        val repository = newRepository()
        repository.setProductivity(TimeFormat.Hour12, 10, sound = false, vibration = false)
        repository.setWeekStart(java.time.DayOfWeek.SUNDAY)
        repository.setMascotSettings(MascotSettings(selectedMascot = "owl", theme = ThemeMode.Dark))
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = newRepository()
        val saved = reopened.settings.first()
        assertEquals(TimeFormat.Hour12, saved.timeFormat); assertEquals(10, saved.defaultReminderOffset)
        assertFalse(saved.defaultAlertSound); assertFalse(saved.defaultAlertVibration)
        assertEquals(java.time.DayOfWeek.SUNDAY, saved.weekStart); assertEquals("owl", saved.selectedMascot)
        reopened.reset(); assertEquals(MascotSettings(), reopened.settings.first())
    }
    @Test fun malformedProductivitySettingsFallBackWithoutOverwritingValidPreferences() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        store.updateData { it.toMutablePreferences().apply {
            this[androidx.datastore.preferences.core.stringPreferencesKey("time_format")] = "Not a format"
            this[androidx.datastore.preferences.core.intPreferencesKey("default_reminder_offset")] = -100
            this[androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")] = "Dark"
        } }
        val saved = DataStorePreferencesRepository(store).settings.first()
        assertEquals(TimeFormat.Hour24, saved.timeFormat); assertNull(saved.defaultReminderOffset)
        assertEquals(ThemeMode.Dark, saved.theme)
    }

    @Test fun studioSavePreservesConcurrentWeekStartAndLegacyPersonalitiesAreMapped() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val repository = DataStorePreferencesRepository(store)
        store.updateData { preferences -> preferences.toMutablePreferences().apply {
            this[androidx.datastore.preferences.core.stringPreferencesKey("personality")] = "Encouraging"
        } }
        assertEquals(MascotPersonality.Friendly, repository.settings.first().personality)
        store.updateData { preferences -> preferences.toMutablePreferences().apply {
            this[androidx.datastore.preferences.core.stringPreferencesKey("personality")] = "Quiet"
        } }
        assertEquals(MascotPersonality.Focused, repository.settings.first().personality)
        repository.setWeekStart(java.time.DayOfWeek.MONDAY)
        repository.setMascotSettings(MascotSettings(selectedMascot = "sprout", personality = MascotPersonality.Playful))
        assertEquals(java.time.DayOfWeek.MONDAY, repository.settings.first().weekStart)
        assertEquals("sprout", repository.settings.first().selectedMascot)
    }

    @Test fun settingsAndThemeSurviveStoreRecreation() = runTest {
        val repository = newRepository()
        val settings = MascotSettings(selectedMascot = "owl", personality = MascotPersonality.Friendly,
            supportedColors = setOf(0xFF112233, 0xFF445566), selectedColor = 0xFF445566,
            theme = ThemeMode.Dark, animationsEnabled = false, soundEnabled = true, showMascot = false)
        repository.setSettings(settings)
        assertEquals(settings, repository.settings.first())
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = newRepository()
        assertEquals(settings, reopened.settings.first())
        reopened.setTheme(ThemeMode.Light)
        assertEquals(settings.copy(theme = ThemeMode.Light), reopened.settings.first())
    }

    @Test fun existingSharedPreferencesThemeMigratesOnceAndNewValueWins() = runTest {
        val legacyName = "appearance-${UUID.randomUUID()}"
        val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        assertTrue(legacy.edit().putString("theme_mode", "Dark").commit())
        val repository = newRepository(legacyName)
        assertEquals(ThemeMode.Dark, repository.settings.first().theme)
        assertFalse(legacy.contains("theme_mode"))
        repository.setTheme(ThemeMode.Light)
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        legacy.edit().putString("theme_mode", "Dark").commit()
        assertEquals(ThemeMode.Light, newRepository(legacyName).settings.first().theme)
        legacy.edit().clear().commit()
    }

    @Test fun weekStartSurvivesRecreationAndPreservesAppearance() = runTest {
        val repository = newRepository()
        repository.setTheme(ThemeMode.Dark)
        repository.setWeekStart(java.time.DayOfWeek.SATURDAY)
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = newRepository()
        assertEquals(java.time.DayOfWeek.SATURDAY, reopened.settings.first().weekStart)
        assertEquals(ThemeMode.Dark, reopened.settings.first().theme)
        reopened.setWeekStart(null)
        assertNull(reopened.settings.first().weekStart)
        assertEquals(ThemeMode.Dark, reopened.settings.first().theme)
    }
}
