package com.example.platform

import android.Manifest
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
        private const val NOTIF_BLINK_ID = 2501
        private const val NOTIF_EYE_REST_ID = 2502
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
                description = "Discreet low-priority blink cues that never interrupt focus"
                setSound(null, null)
                enableVibration(false)
            }
            val restChannel = NotificationChannel(
                CHANNEL_EYE_REST,
                "Eye-Rest Breaks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic 20-20-20 eye-rest break reminders"
                setSound(null, null)
            }
            manager.createNotificationChannel(blinkChannel)
            manager.createNotificationChannel(restChannel)
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
            .setTimeoutAfter(8000L)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_BLINK_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked externally
        }
    }

    fun postEyeRestCue(title: String, body: String) {
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
        val notification = NotificationCompat.Builder(context, CHANNEL_EYE_REST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_EYE_REST_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked externally
        }
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
