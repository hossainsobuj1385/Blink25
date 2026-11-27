package com.example.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class ReminderNotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_BLINK = "blink25_gentle_blink"
        const val CHANNEL_EYE_REST = "blink25_eye_rest"
        const val CHANNEL_SCREEN_MONITOR = "blink25_screen_monitor"
        private const val NOTIF_BLINK_ID = 2501
        private const val NOTIF_EYE_REST_ID = 2502
        const val NOTIF_SCREEN_MONITOR_ID = 2503
        private const val BLINK_TIMEOUT_MS = 4500L
    }

    init {
        createChannels()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val blinkChannel = NotificationChannel(
                CHANNEL_BLINK,
                "Gentle Blink Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Discreet blink cues that auto-dismiss after a few seconds"
                setSound(null, null)
                enableVibration(false)
            }
            val restChannel = NotificationChannel(
                CHANNEL_EYE_REST,
                "Eye-Rest Breaks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic 20-20-20 eye-rest break reminders that auto-dismiss when finished"
                setSound(null, null)
            }
            val monitorChannel = NotificationChannel(
                CHANNEL_SCREEN_MONITOR,
                "Screen-Active Eye Guard",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Lightweight background monitor active only while screen is on"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(blinkChannel)
            manager.createNotificationChannel(restChannel)
            manager.createNotificationChannel(monitorChannel)
        }
    }

    fun postGentleBlinkCue(title: String, body: String) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BLINK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(BLINK_TIMEOUT_MS)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_BLINK_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked externally
        }
    }

    fun postEyeRestCue(title: String, body: String, durationSeconds: Int = 20) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val timeoutMs = (durationSeconds.coerceIn(5, 180) * 1000L)
        val notification = NotificationCompat.Builder(context, CHANNEL_EYE_REST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(timeoutMs)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_EYE_REST_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked externally
        }
    }

    fun buildScreenMonitorNotification(title: String, body: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SCREEN_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun cancelBlinkCue() {
        NotificationManagerCompat.from(context).cancel(NOTIF_BLINK_ID)
    }

    fun cancelEyeRestCue() {
        NotificationManagerCompat.from(context).cancel(NOTIF_EYE_REST_ID)
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancel(NOTIF_BLINK_ID)
        NotificationManagerCompat.from(context).cancel(NOTIF_EYE_REST_ID)
    }
}
