package com.focusbuddy.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.focusbuddy.domain.model.*
import com.focusbuddy.ui.components.LocalMascotSettings
import com.focusbuddy.ui.theme.FocusBuddyTheme
import com.focusbuddy.ui.today.TimeField
import com.focusbuddy.ui.reminders.ReminderSetupNotice
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.focusbuddy.TestApplication::class, qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimePickerUiTest {
    @get:Rule val compose = createComposeRule()
    private var value by mutableStateOf("09:00")
    private fun render(format: TimeFormat = TimeFormat.Hour12) {
        compose.setContent { FocusBuddyTheme(ThemeMode.Light) {
            CompositionLocalProvider(LocalMascotSettings provides MascotSettings(timeFormat = format)) {
                Surface { TimeField(value, "Start time", { value = it }) }
            }
        } }
        compose.onNodeWithText("Choose start time").performClick()
        compose.onNodeWithTag("time-dial").assertExists()
        java.io.File("build/reports/qa/time-picker-${format.name}-semantics.txt").also { it.parentFile?.mkdirs() }
            .writeText(compose.onAllNodes(isRoot(), useUnmergedTree = true).onLast().printToString())
    }
    @Test fun analogHourAndMinuteSelectionCommitsToTheTaskField() {
        render()
        compose.onNodeWithContentDescription("3", substring = true, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Select minutes").performClick()
        compose.onNodeWithContentDescription("30 minutes").performClick()
        compose.onNodeWithText("Use time").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("03:30", value) }
        compose.onNodeWithTag("time-dial").assertDoesNotExist()
    }
    @Test fun keyboardAndClockShareTheSelectionIn24HourMode() {
        render(TimeFormat.Hour24)
        compose.onNodeWithText("Use keyboard").performScrollTo().performClick()
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("time-keyboard")))
        fields[0].performTextReplacement("17")
        fields[1].performTextReplacement("42")
        compose.onNodeWithText("Use clock").performScrollTo().performClick()
        compose.onNodeWithTag("time-dial").assertExists()
        compose.onNodeWithText("Use time").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("17:42", value) }
    }
    @Test fun cancelDoesNotChangeTheTaskTime() {
        render()
        compose.onNodeWithContentDescription("3", substring = true, useUnmergedTree = true).performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("09:00", value) }
        compose.onNodeWithText("Choose start time").performClick()
        compose.onNodeWithText("Use time").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("09:00", value) }
    }
    @Test fun selectedClockTimeSurvivesSavedStateRestorationBeforeConfirmation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FocusBuddyTheme(ThemeMode.Light) {
            CompositionLocalProvider(LocalMascotSettings provides MascotSettings(timeFormat = TimeFormat.Hour12)) {
                Surface { TimeField(value, "Start time", { value = it }) }
            }
        } }
        compose.onNodeWithText("Choose start time").performClick()
        compose.onNodeWithContentDescription("3", substring = true, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Select minutes").performClick()
        compose.onNodeWithContentDescription("30 minutes").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("time-dial").assertExists()
        compose.onNodeWithText("Use time").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("03:30", value) }
    }
    @Test fun permissionNoticeOffersReviewWithoutClaimingDeliveryIsEnabled() {
        var reviewed = false
        compose.setContent { FocusBuddyTheme(ThemeMode.Light) {
            ReminderSetupNotice(onDismiss = {}, onReview = { reviewed = true })
        } }
        compose.onNodeWithText("Reminder saved · notifications are off").assertExists()
        compose.onNodeWithText("Enable notifications").performClick()
        compose.runOnIdle { assertTrue(reviewed) }
    }
    @Test fun preciseAlarmWarningDoesNotMisreportGrantedNotificationPermission() {
        var reviewed = false
        compose.setContent { FocusBuddyTheme(ThemeMode.Light) {
            ReminderSetupNotice(onDismiss = {}, onReview = { reviewed = true }, notificationsEnabled = true)
        } }
        compose.onNodeWithText("Reminder saved · timing may be delayed").assertExists()
        compose.onNodeWithText("Enable notifications").assertDoesNotExist()
        compose.onNodeWithText("Review precise timing").performClick()
        compose.runOnIdle { assertTrue(reviewed) }
    }
}
