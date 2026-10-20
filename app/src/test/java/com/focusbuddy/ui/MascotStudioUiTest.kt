package com.focusbuddy.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.*
import androidx.test.core.app.ApplicationProvider
import com.focusbuddy.data.preferences.DataStorePreferencesRepository
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.mascot.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class MascotStudioUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "studio-${UUID.randomUUID()}.preferences_pb")
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private fun repository() = DataStorePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    private fun model(repository: DataStorePreferencesRepository, handle: SavedStateHandle = SavedStateHandle()): MascotStudioViewModel {
        val store = ViewModelStore().also(stores::add)
        return MascotStudioViewModel(repository, handle).also { store.put("studio", it) }
    }
    @Before fun prepare() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() {
        try {
            stores.forEach { it.clear() }
            closeStore()
            file.delete()
        } finally { Dispatchers.resetMain() }
    }
    private fun closeStore() {
        val job = scope.coroutineContext.job
        job.cancel()
        // Compose owns the test scheduler: keep pumping it during DataStore cancellation.
        compose.waitUntil(10_000) { job.isCompleted }
    }
    @Test fun selectionUpdatesReminderPreviewBeforeSavingAndPersistsAfterRecreation() {
        val repository = repository()
        var current by mutableStateOf(model(repository))
        compose.setContent { MascotStudioRoute(current) }
        compose.waitUntil(10_000) { current.state.value.loaded }
        compose.onNodeWithTag("mascot-owl").performScrollTo().performClick().assertIsSelected()
        // Draw the host view directly: Robolectric does not drive PixelCopy's redraw callback.
        compose.runOnIdle {
            val host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val view = host.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File("build/reports/mascot-studio.png").also { it.parentFile?.mkdirs() }.outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertEquals("bunny", runBlocking { repository.settings.first().selectedMascot })
        compose.onNodeWithTag("mascot-reminder-preview").performScrollTo()
        compose.onNodeWithContentDescription("Reminder companion, Luma", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Reminder preview · 9:00 AM").assertIsDisplayed()
        compose.onNodeWithText("Save companion").performScrollTo().performClick()
        compose.waitUntil(10_000) { current.state.value.saved }
        assertEquals("owl", runBlocking { repository.settings.first().selectedMascot })
        stores.forEach { it.clear() }
        closeStore()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = repository()
        compose.runOnIdle { current = model(reopened) }
        compose.waitUntil(10_000) { current.state.value.loaded }
        compose.onNodeWithTag("mascot-owl").performScrollTo().assertIsSelected()
        assertEquals("owl", runBlocking { reopened.settings.first().selectedMascot })
    }
    @Test fun personalityAndVisibilityAreReflectedInReminderPreviewAndResetNeedsSave() {
        val repository = repository()
        val model = model(repository)
        compose.setContent { MascotStudioRoute(model) }
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.onNodeWithText("Playful").performScrollTo().performClick()
        compose.onNodeWithTag("mascot-reminder-preview").performScrollTo()
        compose.onNodeWithText("A little tap on the shoulder.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show companion in Today and reminders").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Reminder companion, Mallow", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        compose.onNodeWithText("Reset preview").performClick()
        assertEquals(MascotSettings(), model.state.value.draft)
        assertTrue(model.state.value.dirty)
        assertEquals(MascotSettings(), runBlocking { repository.settings.first() })
    }
    @Test fun unsavedPreviewSurvivesViewModelRecreationWithoutChangingPersistedCompanion() {
        val repository = repository()
        val handle = SavedStateHandle()
        var current by mutableStateOf(model(repository, handle))
        compose.setContent { MascotStudioRoute(current) }
        compose.waitUntil(10_000) { current.state.value.loaded }
        compose.onNodeWithTag("mascot-sprout").performScrollTo().performClick()
        compose.runOnIdle { current = model(repository, SavedStateHandle(mapOf("mascotDraft" to handle.get<android.os.Bundle>("mascotDraft")))) }
        compose.waitUntil(10_000) { current.state.value.loaded }
        compose.onNodeWithTag("mascot-sprout").assertIsSelected()
        assertEquals("bunny", runBlocking { repository.settings.first().selectedMascot })
        compose.onNodeWithText("Discard preview changes").performScrollTo().performClick()
        compose.waitUntil(10_000) { current.state.value.draft?.selectedMascot == "bunny" }
    }
}
