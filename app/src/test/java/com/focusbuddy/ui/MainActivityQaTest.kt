package com.focusbuddy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import com.focusbuddy.FocusBuddyApplication
import com.focusbuddy.MainActivity
import com.focusbuddy.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.rules.TestName
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*
import java.time.format.*
import java.io.File

/** Real Activity and repositories on JVM shadows, not emulator/device evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class, qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityQaTest {
    @get:Rule(order = 0) val dispatcher = object : TestWatcher() {
        override fun starting(description: Description) { Dispatchers.setMain(UnconfinedTestDispatcher()) }
        override fun finished(description: Description) { Dispatchers.resetMain() }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 2) val caseName = TestName()
    @After fun inspectRuntimeLogs() {
        val logs = org.robolectric.shadows.ShadowLog.getLogs()
        File("build/reports/qa/${caseName.methodName}-log.txt").also { it.parentFile?.mkdirs() }.writeText(
            logs.filter { it.type >= android.util.Log.WARN }.joinToString("\n") {
                "${it.type}/${it.tag}: ${it.msg}" + (it.throwable?.stackTraceToString()?.let { stack -> "\n$stack" } ?: "")
            })
        assertFalse("Unhandled Android runtime crash in host UI run", logs.any {
            it.type >= android.util.Log.ERROR && (it.tag == "AndroidRuntime" || it.msg.contains("FATAL EXCEPTION"))
        })
    }
    private val container get() = (compose.activity.application as FocusBuddyApplication).container
    private fun navigate(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
    private fun awaitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        File("build/reports/qa/$name.png").also { it.parentFile?.mkdirs() }.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun focusPauseNavigationThemeAndActivityRecreationPreserveTheSession() {
        navigate("Focus"); awaitText("Start focus")
        compose.onNodeWithText("Start focus").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.focusTimer.snapshot() }?.running == true }
        awaitText("Pause")
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.focusTimer.snapshot() }?.running == false }
        awaitText("Resume")
        val paused = runBlocking { container.focusTimer.snapshot() }!!
        navigate("Profile"); awaitText("Dark")
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.preferences.settings.first().theme } == ThemeMode.Dark }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Dark").performScrollTo().assertIsSelected()
        capture("settings-dark-320")
        navigate("Focus"); awaitText("Resume")
        compose.onNodeWithText("Resume").performScrollTo().assertIsDisplayed()
        assertEquals(paused, runBlocking { container.focusTimer.snapshot() })
        capture("focus-paused-dark-320")
        compose.onNodeWithText("Resume").performClick()
        compose.waitUntil(10_000) { runBlocking { container.focusTimer.snapshot() }?.running == true }
        awaitText("Stop session")
        compose.onNodeWithText("Stop session").performScrollTo().performClick()
        compose.onAllNodesWithText("Stop session").onLast().performClick()
        compose.waitUntil(10_000) { runBlocking { container.focusTimer.snapshot() } == null }
        val history = runBlocking { container.focusSessions.observeAll().first() }
        assertEquals(1, history.size); assertEquals(FocusOutcome.Stopped, history.single().outcome)
        assertFalse(history.single().completed)
    }
    @Test fun selectedExploreDateCreationDoubleTapCountsAndEditingStaySynchronized() {
        val today = LocalDate.now()
        val selected = today.plusDays(1)
        navigate("Explore"); awaitText("Next")
        if (YearMonth.from(selected) != YearMonth.from(today)) compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        compose.onNodeWithContentDescription(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), substring = true).performScrollTo().performClick()
        compose.onNodeWithTag("explore-agenda").performScrollToNode(hasText("+ Add task"))
        compose.onNodeWithText("+ Add task").performClick()
        compose.onNodeWithText(selected.toString()).assertExists()
        compose.onNodeWithText("Task title / required").performTextInput("QA selected-day task")
        val save = compose.onNodeWithText("Create task").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { save(); save() }
        compose.waitUntil(10_000) { runBlocking { container.tasks.observeAll().first().size } == 1 }
        var stored = runBlocking { container.tasks.observeAll().first().single() }
        assertEquals(selected, stored.scheduledDate)
        compose.onNodeWithTag("explore-agenda").performScrollToNode(hasText("QA selected-day task"))
        compose.onNodeWithText("QA selected-day task").performClick()
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        compose.onNodeWithText("Task title / required").performTextReplacement("QA moved task")
        compose.onNodeWithText("Date / YYYY-MM-DD").performTextReplacement(selected.plusDays(1).toString())
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { runBlocking { container.tasks.observeAll().first().single().scheduledDate } == selected.plusDays(1) }
        stored = runBlocking { container.tasks.observeAll().first().single() }
        assertEquals("QA moved task", stored.title)
        val snapshot = runBlocking { container.calendar.observeSnapshot(YearMonth.from(stored.scheduledDate)).first() }
        if (YearMonth.from(selected) == YearMonth.from(stored.scheduledDate)) assertEquals(0, snapshot.days.first { it.date == selected }.taskCount)
        assertEquals(1, snapshot.days.first { it.date == stored.scheduledDate }.taskCount)
        navigate("Today")
        compose.onNodeWithTag("today-agenda").performScrollToNode(hasText("QA moved task"))
        compose.onNodeWithText("QA moved task").assertIsDisplayed()
        capture("today-selected-320")
    }
    @Test fun clearAllRequiresConfirmationAndRemovesRecordsAndPreferences() {
        val source = container
        val task = runBlocking { source.tasks.create(Task(title = "Keep until confirmed", scheduledDate = LocalDate.now())) }
        navigate("Profile"); awaitText("Clear all data")
        compose.onNodeWithText("Clear all data").performScrollTo().performClick()
        compose.onNodeWithText("Clear all FocusBuddy data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(runBlocking { source.tasks.get(task.id) })
        compose.onNodeWithText("Clear all data").performClick()
        compose.onAllNodesWithText("Clear all data").onLast().performClick()
        compose.waitUntil(10_000) { runBlocking { source.tasks.observeAll().first().isEmpty() } }
        assertTrue(runBlocking { source.reminders.observeAll().first().isEmpty() })
        assertNull(runBlocking { source.focusTimer.snapshot() })
        assertEquals(MascotSettings(), runBlocking { source.preferences.settings.first() })
    }
}
