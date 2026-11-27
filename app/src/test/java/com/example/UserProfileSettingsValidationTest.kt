package com.example

import com.example.domain.BlinkValidators
import com.example.domain.ProfileType
import com.example.domain.ReminderProfile
import com.example.domain.SoundType
import com.example.domain.ThemeMode
import com.example.domain.UserAccount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileSettingsValidationTest {

    @Test
    fun userAccountStructure_acceptsValidAccountAndRejectsViolations() {
        val valid = UserAccount(
            userId = "user_abc_123",
            displayName = "Hasan",
            email = "ha2420331@gmail.com",
            themeMode = ThemeMode.DARK.name,
            languageCode = "bn",
            activeProfileId = "work_default",
            soundType = SoundType.CALM_CHIME.name,
            soundVolume = 0.75,
            meetingModeMinutes = 45L,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00",
            timezone = "Asia/Dhaka"
        )
        assertTrue(BlinkValidators.validateUserAccountStructure(valid).isValid)

        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(userId = "bad uid!")).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(displayName = "   ")).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(languageCode = "fr")).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(themeMode = "NEON")).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(soundVolume = 1.5)).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(soundVolume = Double.NaN)).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(meetingModeMinutes = 2L)).isValid)
        assertFalse(BlinkValidators.validateUserAccountStructure(valid.copy(quietHoursStart = "25:00")).isValid)
    }

    @Test
    fun userSettingsUpdatePatch_rejectsUnknownAndInvalidFields() {
        val validPatch = mapOf(
            "displayName" to "  Rahim  ",
            "themeMode" to ThemeMode.LIGHT.name,
            "languageCode" to "en",
            "soundVolume" to 0.5,
            "quietHoursStart" to "23:15"
        )
        assertTrue(BlinkValidators.validateUserSettingsUpdates(validPatch).isValid)

        assertFalse(BlinkValidators.validateUserSettingsUpdates(mapOf("unknownKey" to true)).isValid)
        assertFalse(BlinkValidators.validateUserSettingsUpdates(mapOf("displayName" to "")).isValid)
        assertFalse(BlinkValidators.validateUserSettingsUpdates(mapOf("soundType" to "LOUD_ALARM")).isValid)
        assertFalse(BlinkValidators.validateUserSettingsUpdates(mapOf("soundVolume" to -0.1)).isValid)
        assertFalse(BlinkValidators.validateUserSettingsUpdates(mapOf("quietHoursEnd" to "9:00")).isValid)
        assertFalse(BlinkValidators.validateUserSettingsUpdates(mapOf("meetingModeMinutes" to 999)).isValid)
    }

    @Test
    fun reminderProfileStructure_validatesAllProfileFields() {
        val valid = ReminderProfile(
            id = "prof_work_1",
            userId = "user_abc_123",
            name = "Deep Coding",
            profileType = ProfileType.WORK.name,
            blinkIntervalSeconds = 25L,
            earlyWarningSeconds = 5L,
            eyeRestIntervalMinutes = 20L,
            eyeRestDurationSeconds = 20L,
            soundType = SoundType.GENTLE_BELL.name,
            soundVolume = 0.6,
            snoozeMinutes = 5L
        )
        assertTrue(BlinkValidators.validateReminderProfileStructure(valid, requireExistingId = true).isValid)

        assertFalse(
            BlinkValidators.validateReminderProfileStructure(valid.copy(id = ""), requireExistingId = true).isValid
        )
        assertFalse(
            BlinkValidators.validateReminderProfileStructure(valid.copy(profileType = "UNKNOWN")).isValid
        )
        assertFalse(
            BlinkValidators.validateReminderProfileStructure(valid.copy(soundType = "BEEP")).isValid
        )
        assertFalse(
            BlinkValidators.validateReminderProfileStructure(valid.copy(snoozeMinutes = 0L)).isValid
        )
        assertFalse(
            BlinkValidators.validateReminderProfileStructure(valid.copy(earlyWarningSeconds = 25L)).isValid
        )
        assertFalse(
            BlinkValidators.validateReminderProfileStructure(valid.copy(soundVolume = 1.2)).isValid
        )
    }
}
