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
@Config(sdk = [35])
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

    @Test fun settingsAndThemeSurviveStoreRecreation() = runTest {
        val repository = newRepository()
        val settings = MascotSettings(selectedMascot = "bunny", personality = MascotPersonality.Encouraging,
            supportedColors = setOf(0xFF112233, 0xFF445566), selectedColor = 0xFF445566,
            theme = ThemeMode.Dark, animationsEnabled = false, soundEnabled = true)
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
}
