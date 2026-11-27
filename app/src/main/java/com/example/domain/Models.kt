package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class SoundType(val displayNameEn: String, val displayNameBn: String) {
    SOFT_TICK("Soft Tick", "হালকা টিক"),
    GENTLE_BELL("Gentle Bell", "মৃদু ঘণ্টা"),
    SOFT_POP("Soft Pop", "নরম পপ"),
    CALM_CHIME("Calm Chime", "শান্ত ধ্বনি"),
    AIR_TONE("Air Tone", "বাতাসের সুর"),
    QUIET_PULSE("Quiet Pulse", "নীরব স্পন্দন"),
    WATER_DROP("Water Drop", "জলের ফোঁটা"),
    CRYSTAL_PING("Crystal Ping", "ক্রিস্টাল পিং"),
    BAMBOO_TAP("Bamboo Tap", "বাঁশের টোকা"),
    MORNING_BIRD("Morning Chirp", "পাখির ডাক"),
    BRIGHT_CHORD("Bright Chord", "উজ্জ্বল সুর"),
    NO_SOUND("No Sound", "নিঃশব্দ");

    companion object {
        fun fromRaw(raw: String?): SoundType =
            entries.firstOrNull { it.name == raw } ?: GENTLE_BELL
    }
}

enum class ProfileType(val defaultLabelEn: String, val defaultLabelBn: String) {
    WORK("Work", "কাজ"),
    STUDY("Study", "পড়াশোনা"),
    GAMING("Gaming", "গেমিং"),
    MEETING("Meeting", "মিটিং"),
    RELAX("Relax", "বিশ্রাম"),
    CUSTOM("Custom", "কাস্টম");

    companion object {
        fun fromRaw(raw: String?): ProfileType =
            entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class AccountStatus {
    ACTIVE, SUSPENDED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class SupportStatus {
    OPEN, RESOLVED;

    companion object {
        fun fromRaw(raw: String?): SupportStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val accountStatus: String = AccountStatus.ACTIVE.name,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val languageCode: String = "en",
    val activeProfileId: String = "work_default",
    val soundEnabled: Boolean = true,
    val soundType: String = SoundType.GENTLE_BELL.name,
    val soundVolume: Double = 0.6,
    val notificationsEnabled: Boolean = true,
    val focusModeActive: Boolean = false,
    val meetingModeActive: Boolean = false,
    val meetingModeMinutes: Long = 30L,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val timezone: String = "UTC",
    val reducedMotion: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReminderProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Work",
    val profileType: String = ProfileType.WORK.name,
    val blinkIntervalSeconds: Long = 25L,
    val earlyWarningSeconds: Long = 5L,
    val eyeRestIntervalMinutes: Long = 20L,
    val eyeRestDurationSeconds: Long = 20L,
    val twentyTwentyTwentyEnabled: Boolean = true,
    val soundType: String = SoundType.GENTLE_BELL.name,
    val soundVolume: Double = 0.6,
    val visualReminderEnabled: Boolean = true,
    val snoozeMinutes: Long = 5L,
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReminderSchedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileId: String = "work_default",
    val scheduleName: String = "Workday Focus",
    val startTime: String = "09:00",
    val endTime: String = "17:00",
    val weekdays: List<Long> = listOf(1L, 2L, 3L, 4L, 5L),
    val timezone: String = "UTC",
    val blinkEnabled: Boolean = true,
    val eyeRestEnabled: Boolean = true,
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SessionSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileId: String = "",
    val profileName: String = "",
    val durationSeconds: Long = 0L,
    val blinksTriggered: Long = 0L,
    val eyeRestsCompleted: Long = 0L,
    val eyeRestsSkipped: Long = 0L,
    val dayKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val message: String = "",
    val status: String = SupportStatus.OPEN.name,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val action: String = "",
    val targetUserId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun buildDefaultProfiles(userId: String): List<Pair<String, ReminderProfile>> = listOf(
    "work_default" to ReminderProfile(
        userId = userId,
        name = "Work",
        profileType = ProfileType.WORK.name,
        blinkIntervalSeconds = 25L,
        earlyWarningSeconds = 5L,
        eyeRestIntervalMinutes = 20L,
        eyeRestDurationSeconds = 20L,
        twentyTwentyTwentyEnabled = true,
        soundType = SoundType.GENTLE_BELL.name,
        soundVolume = 0.6,
        visualReminderEnabled = true,
        snoozeMinutes = 5L,
        enabled = true
    ),
    "study_default" to ReminderProfile(
        userId = userId,
        name = "Study",
        profileType = ProfileType.STUDY.name,
        blinkIntervalSeconds = 20L,
        earlyWarningSeconds = 5L,
        eyeRestIntervalMinutes = 25L,
        eyeRestDurationSeconds = 20L,
        twentyTwentyTwentyEnabled = true,
        soundType = SoundType.CALM_CHIME.name,
        soundVolume = 0.5,
        visualReminderEnabled = true,
        snoozeMinutes = 5L,
        enabled = true
    ),
    "gaming_default" to ReminderProfile(
        userId = userId,
        name = "Gaming",
        profileType = ProfileType.GAMING.name,
        blinkIntervalSeconds = 30L,
        earlyWarningSeconds = 5L,
        eyeRestIntervalMinutes = 30L,
        eyeRestDurationSeconds = 30L,
        twentyTwentyTwentyEnabled = false,
        soundType = SoundType.SOFT_POP.name,
        soundVolume = 0.5,
        visualReminderEnabled = true,
        snoozeMinutes = 5L,
        enabled = true
    ),
    "meeting_default" to ReminderProfile(
        userId = userId,
        name = "Meeting",
        profileType = ProfileType.MEETING.name,
        blinkIntervalSeconds = 30L,
        earlyWarningSeconds = 0L,
        eyeRestIntervalMinutes = 45L,
        eyeRestDurationSeconds = 20L,
        twentyTwentyTwentyEnabled = false,
        soundType = SoundType.NO_SOUND.name,
        soundVolume = 0.0,
        visualReminderEnabled = true,
        snoozeMinutes = 10L,
        enabled = true
    ),
    "relax_default" to ReminderProfile(
        userId = userId,
        name = "Relax",
        profileType = ProfileType.RELAX.name,
        blinkIntervalSeconds = 40L,
        earlyWarningSeconds = 5L,
        eyeRestIntervalMinutes = 20L,
        eyeRestDurationSeconds = 30L,
        twentyTwentyTwentyEnabled = true,
        soundType = SoundType.AIR_TONE.name,
        soundVolume = 0.4,
        visualReminderEnabled = true,
        snoozeMinutes = 10L,
        enabled = true
    )
)
