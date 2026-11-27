package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.domain.AccountStatus
import com.example.i18n.LocalStrings
import com.example.i18n.englishStrings
import com.example.ui.components.WorkSessionStatusCard
import com.example.ui.theme.Blink25Theme
import com.example.viewmodel.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkSessionStatusCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysCurrentWorkSessionStatusAndElapsedDuration() {
        composeTestRule.setContent {
            Blink25Theme {
                CompositionLocalProvider(LocalStrings provides englishStrings()) {
                    WorkSessionStatusCard(
                        engineStatus = EngineStatus.RUNNING,
                        sessionElapsedSeconds = 125,
                        profileName = "Deep Focus",
                        activeScheduleName = "Weekday Morning",
                        isQuietHoursNow = false,
                        accountStatus = AccountStatus.ACTIVE.name,
                        isEyeRestActive = false,
                        nextEyeRestSeconds = 900,
                        eyeRestRemainingSeconds = 20,
                        eyeRestDurationSeconds = 20,
                        onStartEyeRest = {},
                        onStopEyeRest = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("work_session_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work Session Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("work_session_elapsed_text").assertTextContains("02:05")
        composeTestRule.onNodeWithText("Profile: Deep Focus • Weekday Morning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eye_rest_countdown_text").assertTextContains("15:00", substring = true)
        composeTestRule.onNodeWithTag("eye_rest_start_stop_button").assertTextContains("Start Eye Rest")
    }

    @Test
    fun startStopButtonTogglesEyeRestTimerState() {
        var startedCount = 0
        var stoppedCount = 0

        composeTestRule.setContent {
            var isEyeRestActive by remember { mutableStateOf(false) }
            Blink25Theme {
                CompositionLocalProvider(LocalStrings provides englishStrings()) {
                    WorkSessionStatusCard(
                        engineStatus = EngineStatus.IDLE,
                        sessionElapsedSeconds = 0,
                        profileName = "Work",
                        activeScheduleName = null,
                        isQuietHoursNow = false,
                        accountStatus = AccountStatus.ACTIVE.name,
                        isEyeRestActive = isEyeRestActive,
                        nextEyeRestSeconds = 1200,
                        eyeRestRemainingSeconds = 20,
                        eyeRestDurationSeconds = 20,
                        onStartEyeRest = {
                            startedCount += 1
                            isEyeRestActive = true
                        },
                        onStopEyeRest = {
                            stoppedCount += 1
                            isEyeRestActive = false
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eye_rest_start_stop_button")
            .assertTextContains("Start Eye Rest")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, startedCount)
        composeTestRule.onNodeWithTag("eye_rest_countdown_text").assertTextContains("Resting now", substring = true)
        composeTestRule.onNodeWithTag("eye_rest_start_stop_button")
            .assertTextContains("Stop Eye Rest")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, stoppedCount)
        composeTestRule.onNodeWithTag("eye_rest_start_stop_button").assertTextContains("Start Eye Rest")
    }
}
