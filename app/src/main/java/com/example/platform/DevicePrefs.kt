package com.example.platform

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.ReminderProfile
import com.example.domain.ReminderSchedule
import com.example.domain.SoundType
import com.example.domain.ThemeMode
import com.example.domain.UserAccount
import org.json.JSONArray
import org.json.JSONObject

object BlinkRuntimeCoordinator {
    @Volatile
    var isUiInForeground: Boolean = false
}

data class SavedProfileSnapshot(
    val id: String,
    val blinkIntervalSeconds: Int,
    val eyeRestIntervalMinutes: Int,
    val eyeRestDurationSeconds: Int,
    val soundType: String,
    val soundVolume: Float,
    val visualReminderEnabled: Boolean
)

data class SavedReminderConfig(
    val userId: String = "",
    val activeProfileId: String = "work_default",
    val soundEnabled: Boolean = true,
    val soundType: String = SoundType.GENTLE_BELL.name,
    val soundVolume: Float = 0.7f,
    val notificationsEnabled: Boolean = true,
    val focusModeActive: Boolean = false,
    val meetingModeActive: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val profiles: List<SavedProfileSnapshot> = emptyList(),
    val schedules: List<ReminderSchedule> = emptyList()
)

class DevicePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("blink25_device_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTO_ACTIVE_SCREEN_ON = "auto_active_screen_on"
        private const val KEY_CACHED_THEME = "cached_theme_mode"
        private const val KEY_CACHED_LANG = "cached_language_code"
        private const val KEY_CONFIG_JSON = "saved_reminder_config_json"
    }

    fun isAutoActiveOnScreenOn(): Boolean = prefs.getBoolean(KEY_AUTO_ACTIVE_SCREEN_ON, false)

    fun setAutoActiveOnScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ACTIVE_SCREEN_ON, enabled).apply()
    }

    fun getCachedThemeMode(): ThemeMode =
        ThemeMode.fromRaw(prefs.getString(KEY_CACHED_THEME, ThemeMode.SYSTEM.name))

    fun setCachedThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_CACHED_THEME, mode.name).apply()
    }

    fun getCachedLanguageCode(): String =
        prefs.getString(KEY_CACHED_LANG, "en") ?: "en"

    fun setCachedLanguageCode(code: String) {
        val safe = if (code == "bn") "bn" else "en"
        prefs.edit().putString(KEY_CACHED_LANG, safe).apply()
    }

    fun saveReminderConfig(
        account: UserAccount,
        profiles: List<ReminderProfile>,
        schedules: List<ReminderSchedule>
    ) {
        setCachedThemeMode(ThemeMode.fromRaw(account.themeMode))
        setCachedLanguageCode(account.languageCode)

        val root = JSONObject().apply {
            put("userId", account.userId)
            put("activeProfileId", account.activeProfileId)
            put("soundEnabled", account.soundEnabled)
            put("soundType", account.soundType)
            put("soundVolume", account.soundVolume)
            put("notificationsEnabled", account.notificationsEnabled)
            put("focusModeActive", account.focusModeActive)
            put("meetingModeActive", account.meetingModeActive)
            put("quietHoursEnabled", account.quietHoursEnabled)
            put("quietHoursStart", account.quietHoursStart)
            put("quietHoursEnd", account.quietHoursEnd)

            val profArr = JSONArray()
            profiles.forEach { p ->
                profArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("blinkIntervalSeconds", p.blinkIntervalSeconds.toInt())
                        put("eyeRestIntervalMinutes", p.eyeRestIntervalMinutes.toInt())
                        put("eyeRestDurationSeconds", p.eyeRestDurationSeconds.toInt())
                        put("soundType", p.soundType)
                        put("soundVolume", p.soundVolume)
                        put("visualReminderEnabled", p.visualReminderEnabled)
                    }
                )
            }
            put("profiles", profArr)

            val schedArr = JSONArray()
            schedules.forEach { s ->
                schedArr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("profileId", s.profileId)
                        put("scheduleName", s.scheduleName)
                        put("startTime", s.startTime)
                        put("endTime", s.endTime)
                        put("blinkEnabled", s.blinkEnabled)
                        put("eyeRestEnabled", s.eyeRestEnabled)
                        put("enabled", s.enabled)
                        put("weekdays", JSONArray(s.weekdays))
                    }
                )
            }
            put("schedules", schedArr)
        }
        prefs.edit().putString(KEY_CONFIG_JSON, root.toString()).apply()
    }

    fun loadReminderConfig(): SavedReminderConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return SavedReminderConfig()
        return runCatching {
            val root = JSONObject(raw)
            val profiles = buildList {
                val arr = root.optJSONArray("profiles") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SavedProfileSnapshot(
                            id = obj.optString("id"),
                            blinkIntervalSeconds = obj.optInt("blinkIntervalSeconds", 25).coerceIn(5, 300),
                            eyeRestIntervalMinutes = obj.optInt("eyeRestIntervalMinutes", 20).coerceIn(5, 180),
                            eyeRestDurationSeconds = obj.optInt("eyeRestDurationSeconds", 20).coerceIn(5, 180),
                            soundType = obj.optString("soundType", SoundType.GENTLE_BELL.name),
                            soundVolume = obj.optDouble("soundVolume", 0.7).toFloat(),
                            visualReminderEnabled = obj.optBoolean("visualReminderEnabled", true)
                        )
                    )
                }
            }
            val schedules = buildList {
                val arr = root.optJSONArray("schedules") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val daysArr = obj.optJSONArray("weekdays") ?: JSONArray()
                    val days = buildList {
                        for (d in 0 until daysArr.length()) {
                            add(daysArr.optLong(d))
                        }
                    }
                    add(
                        ReminderSchedule(
                            id = obj.optString("id"),
                            profileId = obj.optString("profileId", "work_default"),
                            scheduleName = obj.optString("scheduleName"),
                            startTime = obj.optString("startTime", "09:00"),
                            endTime = obj.optString("endTime", "17:00"),
                            weekdays = days,
                            blinkEnabled = obj.optBoolean("blinkEnabled", true),
                            eyeRestEnabled = obj.optBoolean("eyeRestEnabled", true),
                            enabled = obj.optBoolean("enabled", true)
                        )
                    )
                }
            }
            SavedReminderConfig(
                userId = root.optString("userId"),
                activeProfileId = root.optString("activeProfileId", "work_default"),
                soundEnabled = root.optBoolean("soundEnabled", true),
                soundType = root.optString("soundType", SoundType.GENTLE_BELL.name),
                soundVolume = root.optDouble("soundVolume", 0.7).toFloat(),
                notificationsEnabled = root.optBoolean("notificationsEnabled", true),
                focusModeActive = root.optBoolean("focusModeActive", false),
                meetingModeActive = root.optBoolean("meetingModeActive", false),
                quietHoursEnabled = root.optBoolean("quietHoursEnabled", false),
                quietHoursStart = root.optString("quietHoursStart", "22:00"),
                quietHoursEnd = root.optString("quietHoursEnd", "07:00"),
                profiles = profiles,
                schedules = schedules
            )
        }.getOrDefault(SavedReminderConfig())
    }
}
