package com.focusbuddy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.runner.lifecycle.*
import com.focusbuddy.domain.calendar.*
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.explore.ExploreState
import com.focusbuddy.ui.screens.*
import com.focusbuddy.ui.theme.FocusBuddyTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class, qualifiers = "w320dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarVisualQaTest {
    @get:Rule val compose = createComposeRule()
    private fun render(theme: ThemeMode, scale: Float) {
        val date = LocalDate.of(2026, 3, 15)
        val tasks = (1..120).map { Task(title = "Task $it", scheduledDate = date) }
        val reminders = (1..120).map { Reminder(title = "Alert $it", triggerAt = date.atTime(9, 0).toInstant(ZoneOffset.UTC)) }
        var selected by mutableStateOf(date)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                FocusBuddyTheme(theme) {
                    FocusBuddyContent(Destination.Explore, {}, theme, {}, todayContent = {}, exploreContent = {
                        ExploreScreen(ExploreState(selected = selected, today = date, firstDay = DayOfWeek.MONDAY, loading = false,
                            snapshot = calendarSnapshot(tasks, reminders, emptyList(), YearMonth.from(selected), ZoneOffset.UTC)),
                            ExploreActions({ selected = it }, { selected = date }, {}, {}, {}, {}, {}))
                    }, animationsEnabled = false)
                }
            }
        }
        compose.onNodeWithContentDescription("120 tasks, 120 reminder alerts", substring = true).performScrollTo().assertIsSelected()
        compose.onNodeWithText("Number: tasks").performScrollTo()
    }
    private fun capture(name: String) = compose.runOnIdle {
        val host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
        val view = host.window.decorView
        val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        File("build/reports/qa/$name.png").also { it.parentFile?.mkdirs() }.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun compactLargeTextKeepsSeparateCountSemanticsAndScrollsThroughSixRows() {
        render(ThemeMode.Dark, 1.5f)
        capture("explore-dark-320-large-text")
        assertEquals(42, monthCells(YearMonth.of(2026, 3), DayOfWeek.MONDAY).size)
        compose.onNodeWithContentDescription(LocalDate.of(2026, 3, 31).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)), substring = true).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        compose.onNodeWithText("April 2026").assertExists()
    }
    @Test @Config(qualifiers = "w800dp-h1000dp") fun largerLightScreenUsesRailAndKeepsCalendarCounts() {
        render(ThemeMode.Light, 1f)
        compose.onNodeWithText("Profile").assertIsDisplayed()
        capture("explore-light-800")
    }
    @Test fun disabledCompanionIsAbsentFromSharedEmptyStatesAndThemeTextHasAccessibleContrast() {
        var selected by mutableStateOf(ThemeMode.Light)
        val palettes = mutableMapOf<ThemeMode, List<Pair<Color, Color>>>()
        compose.setContent {
            com.focusbuddy.ui.theme.FocusBuddyTheme(selected) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    palettes[selected] = listOf(colors.onSurface to colors.surface, colors.onPrimary to colors.primary,
                        colors.onPrimaryContainer to colors.primaryContainer, colors.onSurfaceVariant to colors.surface, colors.error to colors.surface)
                }
                CompositionLocalProvider(com.focusbuddy.ui.components.LocalMascotSettings provides MascotSettings(showMascot = false)) {
                    Surface { com.focusbuddy.ui.components.EmptyState("An empty day", "Ready when you are") }
                }
            }
        }
        for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { selected = theme }; compose.waitForIdle()
            val pairs = checkNotNull(palettes[theme])
            pairs.forEach { (foreground, background) ->
                assertTrue("Insufficient text contrast for $theme", androidx.core.graphics.ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb()) >= 4.5)
            }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertCountEquals(0)
        }
    }
}
