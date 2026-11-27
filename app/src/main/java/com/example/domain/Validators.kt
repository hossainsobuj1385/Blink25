package com.example.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

data class StatsAggregate(
    val totalSessions: Int,
    val totalDurationSeconds: Long,
    val totalBlinksTriggered: Long,
    val totalEyeRestsCompleted: Long,
    val totalEyeRestsSkipped: Long,
    val dailySeries: List<DailyStatPoint>,
    val profileUsage: List<ProfileUsagePoint>
)

data class DailyStatPoint(
    val dayKey: String,
    val label: String,
    val durationMinutes: Int,
    val blinks: Int,
    val eyeRests: Int
)

data class ProfileUsagePoint(
    val profileName: String,
    val durationMinutes: Int,
    val sessions: Int
)

enum class StatsRange {
    DAILY, WEEKLY, MONTHLY
}

object BlinkValidators {
    val BLINK_PRESETS = listOf(10, 15, 20, 25, 30, 40, 50, 60)
    val EARLY_WARNING_PRESETS = listOf(0, 1, 5, 10, 15, 30, 60)
    val EYE_REST_INTERVAL_PRESETS = listOf(10, 15, 20, 30, 45, 60)
    val EYE_REST_DURATION_PRESETS = listOf(10, 20, 30, 40, 60)

    private val TIME_REGEX = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
    private val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    private val ALLOWED_USER_UPDATE_KEYS = setOf(
        "displayName",
        "email",
        "themeMode",
        "languageCode",
        "activeProfileId",
        "soundEnabled",
        "soundType",
        "soundVolume",
        "notificationsEnabled",
        "focusModeActive",
        "meetingModeActive",
        "meetingModeMinutes",
        "quietHoursEnabled",
        "quietHoursStart",
        "quietHoursEnd",
        "timezone",
        "reducedMotion"
    )

    fun validateDocumentId(id: String, fieldLabel: String = "Document ID"): ValidationResult {
        return if (id.length in 1..128 && ID_REGEX.matches(id)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("$fieldLabel must be 1 to 128 alphanumeric, underscore, or hyphen characters.")
        }
    }

    fun validateDisplayName(name: String): ValidationResult {
        val trimmed = name.trim()
        return if (trimmed.length in 1..80) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Display name must be 1 to 80 characters.")
        }
    }

    fun validateSoundVolume(volume: Double): ValidationResult {
        return if (!volume.isNaN() && volume in 0.0..1.0) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Sound volume must be between 0.0 and 1.0.")
        }
    }

    fun validateSnoozeMinutes(minutes: Int): ValidationResult {
        return if (minutes in 1..60) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Snooze duration must be between 1 and 60 minutes.")
        }
    }

    fun validateMeetingMinutes(minutes: Int): ValidationResult {
        return if (minutes in 5..240) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Meeting duration must be between 5 and 240 minutes.")
        }
    }

    fun validateTimezone(timezone: String): ValidationResult {
        val trimmed = timezone.trim()
        return if (trimmed.length in 1..64) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Timezone must be 1 to 64 characters.")
        }
    }

    fun validateBlinkInterval(seconds: Int): ValidationResult {
        return if (seconds in 5..300) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Blink interval must be between 5 and 300 seconds.")
        }
    }

    fun validateEarlyWarning(earlySeconds: Int, blinkSeconds: Int): ValidationResult {
        return when {
            earlySeconds < 0 || earlySeconds > 120 ->
                ValidationResult.Invalid("Early warning must be between 0 and 120 seconds.")
            earlySeconds >= blinkSeconds ->
                ValidationResult.Invalid("Early warning must be shorter than the blink interval ($blinkSeconds s).")
            else -> ValidationResult.Valid
        }
    }

    fun validateEyeRest(intervalMinutes: Int, durationSeconds: Int): ValidationResult {
        return when {
            intervalMinutes !in 5..180 ->
                ValidationResult.Invalid("Eye-rest interval must be between 5 and 180 minutes.")
            durationSeconds !in 5..180 ->
                ValidationResult.Invalid("Eye-rest duration must be between 5 and 180 seconds.")
            else -> ValidationResult.Valid
        }
    }

    fun validateProfileName(name: String): ValidationResult {
        val trimmed = name.trim()
        return if (trimmed.isEmpty() || trimmed.length > 50) {
            ValidationResult.Invalid("Profile name must be 1 to 50 characters.")
        } else {
            ValidationResult.Valid
        }
    }

    fun validateReminderProfileStructure(
        profile: ReminderProfile,
        requireExistingId: Boolean = false
    ): ValidationResult {
        val checks = mutableListOf<ValidationResult>()
        if (requireExistingId || profile.id.isNotEmpty()) {
            checks += validateDocumentId(profile.id, "Profile ID")
        }
        checks += validateDocumentId(profile.userId, "User ID")
        checks += validateProfileName(profile.name)
        checks += if (ProfileType.entries.any { it.name == profile.profileType }) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Unsupported profile type: ${profile.profileType}.")
        }
        checks += validateBlinkInterval(profile.blinkIntervalSeconds.toInt())
        checks += validateEarlyWarning(
            profile.earlyWarningSeconds.toInt(),
            profile.blinkIntervalSeconds.toInt()
        )
        checks += validateEyeRest(
            profile.eyeRestIntervalMinutes.toInt(),
            profile.eyeRestDurationSeconds.toInt()
        )
        checks += if (SoundType.entries.any { it.name == profile.soundType }) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Unsupported sound type: ${profile.soundType}.")
        }
        checks += validateSoundVolume(profile.soundVolume)
        checks += validateSnoozeMinutes(profile.snoozeMinutes.toInt())

        return checks.filterIsInstance<ValidationResult.Invalid>().firstOrNull() ?: ValidationResult.Valid
    }

    fun validateUserAccountStructure(account: UserAccount): ValidationResult {
        val checks = listOf(
            validateDocumentId(account.userId, "User ID"),
            validateDisplayName(account.displayName),
            if (account.email == null || account.email.length <= 120) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Email must be 120 characters or fewer.")
            },
            if (AccountStatus.entries.any { it.name == account.accountStatus }) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Unsupported account status: ${account.accountStatus}.")
            },
            if (ThemeMode.entries.any { it.name == account.themeMode }) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Unsupported theme mode: ${account.themeMode}.")
            },
            if (account.languageCode in setOf("en", "bn")) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Language code must be 'en' or 'bn'.")
            },
            validateDocumentId(account.activeProfileId, "Active profile ID"),
            if (SoundType.entries.any { it.name == account.soundType }) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Unsupported sound type: ${account.soundType}.")
            },
            validateSoundVolume(account.soundVolume),
            validateMeetingMinutes(account.meetingModeMinutes.toInt()),
            if (validateTimeHHMM(account.quietHoursStart) && validateTimeHHMM(account.quietHoursEnd)) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("Quiet hours must use 24-hour HH:mm format.")
            },
            validateTimezone(account.timezone)
        )
        return checks.filterIsInstance<ValidationResult.Invalid>().firstOrNull() ?: ValidationResult.Valid
    }

    fun validateUserSettingsUpdates(updates: Map<String, Any?>): ValidationResult {
        for ((key, rawValue) in updates) {
            if (key !in ALLOWED_USER_UPDATE_KEYS) {
                return ValidationResult.Invalid("Setting '$key' cannot be updated.")
            }
            if (rawValue == null) continue
            val check: ValidationResult = when (key) {
                "displayName" -> (rawValue as? String)?.let(::validateDisplayName)
                    ?: ValidationResult.Invalid("displayName must be a string.")
                "email" -> {
                    val str = rawValue as? String
                        ?: return ValidationResult.Invalid("email must be a string.")
                    if (str.length <= 120) ValidationResult.Valid
                    else ValidationResult.Invalid("Email must be 120 characters or fewer.")
                }
                "themeMode" -> {
                    val str = rawValue as? String
                    if (str != null && ThemeMode.entries.any { it.name == str }) ValidationResult.Valid
                    else ValidationResult.Invalid("Invalid themeMode value.")
                }
                "languageCode" -> {
                    val str = rawValue as? String
                    if (str in setOf("en", "bn")) ValidationResult.Valid
                    else ValidationResult.Invalid("Language code must be 'en' or 'bn'.")
                }
                "activeProfileId" -> (rawValue as? String)?.let { validateDocumentId(it, "Active profile ID") }
                    ?: ValidationResult.Invalid("activeProfileId must be a string.")
                "soundEnabled", "notificationsEnabled", "focusModeActive",
                "meetingModeActive", "quietHoursEnabled", "reducedMotion" -> {
                    if (rawValue is Boolean) ValidationResult.Valid
                    else ValidationResult.Invalid("$key must be a boolean.")
                }
                "soundType" -> {
                    val str = rawValue as? String
                    if (str != null && SoundType.entries.any { it.name == str }) ValidationResult.Valid
                    else ValidationResult.Invalid("Invalid soundType value.")
                }
                "soundVolume" -> (rawValue as? Number)?.toDouble()?.let(::validateSoundVolume)
                    ?: ValidationResult.Invalid("soundVolume must be a number between 0.0 and 1.0.")
                "meetingModeMinutes" -> (rawValue as? Number)?.toInt()?.let(::validateMeetingMinutes)
                    ?: ValidationResult.Invalid("meetingModeMinutes must be an integer between 5 and 240.")
                "quietHoursStart", "quietHoursEnd" -> {
                    val str = rawValue as? String
                    if (str != null && validateTimeHHMM(str)) ValidationResult.Valid
                    else ValidationResult.Invalid("$key must use 24-hour HH:mm format.")
                }
                "timezone" -> (rawValue as? String)?.let(::validateTimezone)
                    ?: ValidationResult.Invalid("timezone must be a string.")
                else -> ValidationResult.Valid
            }
            if (check is ValidationResult.Invalid) return check
        }
        return ValidationResult.Valid
    }

    fun validateTimeHHMM(value: String): Boolean = TIME_REGEX.matches(value.trim())

    fun parseTimeOrNull(value: String): LocalTime? {
        if (!validateTimeHHMM(value)) return null
        return runCatching { LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
    }

    fun isWithinTimeWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start == end) {
            true
        } else if (start.isBefore(end)) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            // Overnight wrap (e.g. 22:00 -> 07:00)
            !now.isBefore(start) || now.isBefore(end)
        }
    }

    fun isScheduleActiveAt(schedule: ReminderSchedule, nowTime: LocalTime, dayOfWeek: DayOfWeek): Boolean {
        if (!schedule.enabled) return false
        val dayNum = dayOfWeek.value.toLong()
        if (dayNum !in schedule.weekdays) return false
        val start = parseTimeOrNull(schedule.startTime) ?: return false
        val end = parseTimeOrNull(schedule.endTime) ?: return false
        return isWithinTimeWindow(nowTime, start, end)
    }

    fun findScheduleConflicts(
        candidate: ReminderSchedule,
        existing: List<ReminderSchedule>
    ): List<ReminderSchedule> {
        if (!candidate.enabled) return emptyList()
        val cStart = parseTimeOrNull(candidate.startTime) ?: return emptyList()
        val cEnd = parseTimeOrNull(candidate.endTime) ?: return emptyList()
        return existing.filter { other ->
            other.id != candidate.id &&
                other.enabled &&
                other.weekdays.any { it in candidate.weekdays } &&
                timesOverlap(cStart, cEnd, parseTimeOrNull(other.startTime), parseTimeOrNull(other.endTime))
        }
    }

    private fun timesOverlap(s1: LocalTime, e1: LocalTime, s2: LocalTime?, e2: LocalTime?): Boolean {
        if (s2 == null || e2 == null) return false
        val segs1 = splitWindow(s1, e1)
        val segs2 = splitWindow(s2, e2)
        return segs1.any { (aStart, aEnd) ->
            segs2.any { (bStart, bEnd) ->
                aStart < bEnd && bStart < aEnd
            }
        }
    }

    private fun splitWindow(start: LocalTime, end: LocalTime): List<Pair<Int, Int>> {
        val s = start.toSecondOfDay()
        val e = end.toSecondOfDay()
        return if (s == e) {
            listOf(0 to 86400)
        } else if (s < e) {
            listOf(s to e)
        } else {
            listOf(s to 86400, 0 to e)
        }
    }

    fun aggregateStatistics(
        sessions: List<SessionSummary>,
        range: StatsRange,
        today: LocalDate = LocalDate.now()
    ): StatsAggregate {
        val cutoff = when (range) {
            StatsRange.DAILY -> today
            StatsRange.WEEKLY -> today.minusDays(6)
            StatsRange.MONTHLY -> today.minusDays(29)
        }
        val filtered = sessions.filter { s ->
            val date = runCatching { LocalDate.parse(s.dayKey) }.getOrNull()
            date != null && !date.isBefore(cutoff) && !date.isAfter(today)
        }

        val totalSessions = filtered.size
        val totalDuration = filtered.sumOf { it.durationSeconds }
        val totalBlinks = filtered.sumOf { it.blinksTriggered }
        val totalRestsCompleted = filtered.sumOf { it.eyeRestsCompleted }
        val totalRestsSkipped = filtered.sumOf { it.eyeRestsSkipped }

        val daysCount = when (range) {
            StatsRange.DAILY -> 1
            StatsRange.WEEKLY -> 7
            StatsRange.MONTHLY -> 14 // show 14 most recent days cleanly on chart
        }
        val byDay = filtered.groupBy { it.dayKey }
        val dailySeries = (daysCount - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val list = byDay[key].orEmpty()
            DailyStatPoint(
                dayKey = key,
                label = if (range == StatsRange.DAILY) "Today" else date.dayOfMonth.toString(),
                durationMinutes = (list.sumOf { it.durationSeconds } / 60L).toInt(),
                blinks = list.sumOf { it.blinksTriggered }.toInt(),
                eyeRests = list.sumOf { it.eyeRestsCompleted }.toInt()
            )
        }

        val profileUsage = filtered
            .groupBy { it.profileName.ifBlank { "Work" } }
            .map { (name, items) ->
                ProfileUsagePoint(
                    profileName = name,
                    durationMinutes = (items.sumOf { it.durationSeconds } / 60L).toInt(),
                    sessions = items.size
                )
            }
            .sortedByDescending { it.durationMinutes }

        return StatsAggregate(
            totalSessions = totalSessions,
            totalDurationSeconds = totalDuration,
            totalBlinksTriggered = totalBlinks,
            totalEyeRestsCompleted = totalRestsCompleted,
            totalEyeRestsSkipped = totalRestsSkipped,
            dailySeries = dailySeries,
            profileUsage = profileUsage
        )
    }
}
