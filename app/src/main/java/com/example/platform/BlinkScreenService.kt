package com.example.platform

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.domain.BlinkValidators
import com.example.domain.SoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class BlinkScreenService : Service() {

    companion object {
        fun syncWithPreference(context: Context, enabled: Boolean, isSignedIn: Boolean) {
            val appCtx = context.applicationContext
            if (enabled && isSignedIn) {
                val intent = Intent(appCtx, BlinkScreenService::class.java)
                runCatching {
                    ContextCompat.startForegroundService(appCtx, intent)
                }
            } else {
                runCatching {
                    appCtx.stopService(Intent(appCtx, BlinkScreenService::class.java))
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private lateinit var prefs: DevicePrefs
    private lateinit var notificationHelper: ReminderNotificationHelper
    private val soundPlayer = SoundPlayer()
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Stop all wakeups immediately when device screen turns off (near-zero battery drain)
                    monitorJob?.cancel()
                    monitorJob = null
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    ensureMonitorLoopRunning()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = DevicePrefs(applicationContext)
        notificationHelper = ReminderNotificationHelper(applicationContext)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isBn = prefs.getCachedLanguageCode() == "bn"
        val title = if (isBn) "Blink25 স্ক্রিন-অন সুরক্ষা চালু" else "Blink25 Screen Guard Active"
        val body = if (isBn) {
            "স্ক্রিন চালু থাকলেই রিমাইন্ডার কাজ করবে, বন্ধ হলে অটো থামবে"
        } else {
            "Active only while screen is on • Pauses automatically on screen off"
        }

        val notif = notificationHelper.buildScreenMonitorNotification(title, body)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ReminderNotificationHelper.NOTIF_SCREEN_MONITOR_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ReminderNotificationHelper.NOTIF_SCREEN_MONITOR_ID, notif)
        }

        if (!prefs.isAutoActiveOnScreenOn()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val pm = getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == true) {
            ensureMonitorLoopRunning()
        } else {
            monitorJob?.cancel()
            monitorJob = null
        }
        return START_STICKY
    }

    private fun ensureMonitorLoopRunning() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            var remainingBlinkSec = 25
            var remainingRestSec = 1200

            while (isActive) {
                if (!prefs.isAutoActiveOnScreenOn()) {
                    stopSelf()
                    break
                }
                val pm = getSystemService(PowerManager::class.java)
                if (pm?.isInteractive != true) {
                    // Screen is off -> exit loop until next ACTION_SCREEN_ON
                    break
                }

                // When the app UI is visible, BlinkViewModel owns live countdown & cues (prevents double-firing)
                if (BlinkRuntimeCoordinator.isUiInForeground) {
                    delay(5000L)
                    continue
                }

                val cfg = prefs.loadReminderConfig()
                val nowDt = LocalDateTime.now()
                val quietNow = if (cfg.quietHoursEnabled) {
                    val s = BlinkValidators.parseTimeOrNull(cfg.quietHoursStart)
                    val e = BlinkValidators.parseTimeOrNull(cfg.quietHoursEnd)
                    if (s != null && e != null) BlinkValidators.isWithinTimeWindow(nowDt.toLocalTime(), s, e) else false
                } else {
                    false
                }
                if (quietNow) {
                    delay(60_000L)
                    continue
                }

                val enabledSchedules = cfg.schedules.filter { it.enabled }
                val activeSchedule = enabledSchedules.firstOrNull {
                    BlinkValidators.isScheduleActiveAt(it, nowDt.toLocalTime(), nowDt.dayOfWeek)
                }

                // If user has enabled weekly schedules and none is active right now, sleep quietly until next minute
                if (enabledSchedules.isNotEmpty() && activeSchedule == null) {
                    delay(60_000L)
                    continue
                }

                val profileId = activeSchedule?.profileId ?: cfg.activeProfileId
                val profile = cfg.profiles.firstOrNull { it.id == profileId }
                    ?: cfg.profiles.firstOrNull()

                val baseBlink = (profile?.blinkIntervalSeconds ?: 25).coerceIn(5, 300)
                val effectiveBlink = if (cfg.focusModeActive) (baseBlink * 2).coerceAtMost(300) else baseBlink
                val restIntervalSec = (profile?.eyeRestIntervalMinutes ?: 20).coerceIn(5, 180) * 60
                val restDurSec = (profile?.eyeRestDurationSeconds ?: 20).coerceIn(5, 180)

                val blinkEnabled = activeSchedule?.blinkEnabled ?: true
                val restEnabled = activeSchedule?.eyeRestEnabled ?: true

                if (remainingBlinkSec > effectiveBlink || remainingBlinkSec <= 0) remainingBlinkSec = effectiveBlink
                if (remainingRestSec > restIntervalSec || remainingRestSec <= 0) remainingRestSec = restIntervalSec

                // Adaptive coarse sleep: wake up only at the next scheduled cue instead of every 1 second!
                val nextWakeSec = when {
                    blinkEnabled && restEnabled -> minOf(remainingBlinkSec, remainingRestSec)
                    blinkEnabled -> remainingBlinkSec
                    restEnabled -> remainingRestSec
                    else -> 60
                }.coerceIn(5, 60)

                delay(nextWakeSec * 1000L)

                if (!isActive || getSystemService(PowerManager::class.java)?.isInteractive != true) break
                if (BlinkRuntimeCoordinator.isUiInForeground) continue

                if (blinkEnabled) remainingBlinkSec -= nextWakeSec
                if (restEnabled) remainingRestSec -= nextWakeSec

                val canPlaySound = cfg.soundEnabled && !cfg.focusModeActive && !cfg.meetingModeActive
                val soundType = SoundType.fromRaw(profile?.soundType ?: cfg.soundType)
                val rawProd = (profile?.soundVolume ?: cfg.soundVolume) * cfg.soundVolume
                val vol = kotlin.math.sqrt(rawProd.coerceIn(0.0f, 1.0f)).coerceIn(0.05f, 1.0f)

                if (restEnabled && remainingRestSec <= 0) {
                    remainingRestSec = restIntervalSec
                    remainingBlinkSec = effectiveBlink
                    if (cfg.notificationsEnabled && !cfg.meetingModeActive) {
                        val isBn = prefs.getCachedLanguageCode() == "bn"
                        notificationHelper.postEyeRestCue(
                            title = if (isBn) "Blink25 • চোখের বিশ্রাম" else "Blink25 • Eye Rest",
                            body = if (isBn) "প্রায় ২০ ফুট দূরে $restDurSec সেকেন্ড তাকান।" else "Look ~20 feet away for $restDurSec seconds.",
                            durationSeconds = restDurSec
                        )
                    }
                    if (canPlaySound) {
                        soundPlayer.play(SoundType.GENTLE_BELL, vol)
                    }
                } else if (blinkEnabled && remainingBlinkSec <= 0) {
                    remainingBlinkSec = effectiveBlink
                    if (cfg.notificationsEnabled && !cfg.meetingModeActive) {
                        val isBn = prefs.getCachedLanguageCode() == "bn"
                        notificationHelper.postGentleBlinkCue(
                            title = "Blink25",
                            body = if (isBn) "স্বাভাবিকভাবে চোখের পলক ফেলুন" else "Take a soft, natural blink"
                        )
                    }
                    if (canPlaySound) {
                        soundPlayer.play(soundType, vol)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
