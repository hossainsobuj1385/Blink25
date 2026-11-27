package com.example

import com.example.domain.BlinkValidators
import com.example.domain.ReminderSchedule
import com.example.domain.SessionSummary
import com.example.domain.StatsRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class BlinkValidatorsTest {

    @Test
    fun blinkIntervalValidation_enforcesBounds() {
        assertTrue(BlinkValidators.validateBlinkInterval(25).isValid)
        assertTrue(BlinkValidators.validateBlinkInterval(5).isValid)
        assertTrue(BlinkValidators.validateBlinkInterval(300).isValid)
        assertFalse(BlinkValidators.validateBlinkInterval(4).isValid)
        assertFalse(BlinkValidators.validateBlinkInterval(301).isValid)
    }

    @Test
    fun earlyWarningValidation_mustBeShorterThanBlinkInterval() {
        assertTrue(BlinkValidators.validateEarlyWarning(0, 25).isValid)
        assertTrue(BlinkValidators.validateEarlyWarning(5, 25).isValid)
        assertFalse(BlinkValidators.validateEarlyWarning(25, 25).isValid)
        assertFalse(BlinkValidators.validateEarlyWarning(30, 25).isValid)
        assertFalse(BlinkValidators.validateEarlyWarning(-1, 25).isValid)
    }

    @Test
    fun eyeRestValidation_enforcesBounds() {
        assertTrue(BlinkValidators.validateEyeRest(20, 20).isValid)
        assertFalse(BlinkValidators.validateEyeRest(4, 20).isValid)
        assertFalse(BlinkValidators.validateEyeRest(20, 4).isValid)
        assertFalse(BlinkValidators.validateEyeRest(181, 20).isValid)
    }

    @Test
    fun quietHours_handlesOvernightWrap() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        assertTrue(BlinkValidators.isWithinTimeWindow(LocalTime.of(23, 30), start, end))
        assertTrue(BlinkValidators.isWithinTimeWindow(LocalTime.of(3, 15), start, end))
        assertFalse(BlinkValidators.isWithinTimeWindow(LocalTime.of(12, 0), start, end))
        assertFalse(BlinkValidators.isWithinTimeWindow(LocalTime.of(7, 0), start, end))
    }

    @Test
    fun scheduleConflictDetection_findsOverlappingDaysAndTimes() {
        val existing = listOf(
            ReminderSchedule(
                id = "s1",
                scheduleName = "Morning Work",
                startTime = "09:00",
                endTime = "12:00",
                weekdays = listOf(1L, 2L, 3L),
                enabled = true
            )
        )
        val overlapping = ReminderSchedule(
            id = "s2",
            scheduleName = "Deep Focus",
            startTime = "11:00",
            endTime = "14:00",
            weekdays = listOf(3L, 4L),
            enabled = true
        )
        val nonOverlappingDay = overlapping.copy(weekdays = listOf(4L, 5L))
        val nonOverlappingTime = overlapping.copy(startTime = "12:00", endTime = "14:00")

        assertEquals(1, BlinkValidators.findScheduleConflicts(overlapping, existing).size)
        assertEquals(0, BlinkValidators.findScheduleConflicts(nonOverlappingDay, existing).size)
        assertEquals(0, BlinkValidators.findScheduleConflicts(nonOverlappingTime, existing).size)
        assertTrue(BlinkValidators.isScheduleActiveAt(existing.first(), LocalTime.of(10, 0), DayOfWeek.MONDAY))
        assertFalse(BlinkValidators.isScheduleActiveAt(existing.first(), LocalTime.of(10, 0), DayOfWeek.SATURDAY))
    }

    @Test
    fun statisticsAggregation_computesAccurateTotalsAndBreakdowns() {
        val today = LocalDate.of(2026, 11, 26)
        val sessions = listOf(
            SessionSummary(
                profileId = "work_default",
                profileName = "Work",
                durationSeconds = 1200,
                blinksTriggered = 48,
                eyeRestsCompleted = 1,
                eyeRestsSkipped = 0,
                dayKey = today.toString()
            ),
            SessionSummary(
                profileId = "study_default",
                profileName = "Study",
                durationSeconds = 600,
                blinksTriggered = 30,
                eyeRestsCompleted = 1,
                eyeRestsSkipped = 1,
                dayKey = today.minusDays(2).toString()
            ),
            SessionSummary(
                profileId = "work_default",
                profileName = "Work",
                durationSeconds = 600,
                blinksTriggered = 20,
                eyeRestsCompleted = 0,
                eyeRestsSkipped = 0,
                dayKey = today.minusDays(20).toString()
            )
        )

        val daily = BlinkValidators.aggregateStatistics(sessions, StatsRange.DAILY, today)
        assertEquals(1, daily.totalSessions)
        assertEquals(1200L, daily.totalDurationSeconds)
        assertEquals(48L, daily.totalBlinksTriggered)

        val weekly = BlinkValidators.aggregateStatistics(sessions, StatsRange.WEEKLY, today)
        assertEquals(2, weekly.totalSessions)
        assertEquals(1800L, weekly.totalDurationSeconds)
        assertEquals(78L, weekly.totalBlinksTriggered)
        assertEquals(2, weekly.profileUsage.size)

        val monthly = BlinkValidators.aggregateStatistics(sessions, StatsRange.MONTHLY, today)
        assertEquals(3, monthly.totalSessions)
        assertEquals(2400L, monthly.totalDurationSeconds)
    }
}
