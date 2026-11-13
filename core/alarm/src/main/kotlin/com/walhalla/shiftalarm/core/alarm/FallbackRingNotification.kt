package com.walhalla.shiftalarm.core.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Inexact fallback ring (exact alarms off, in practice API 31-32). The receiver may not start
 * the FGS from the background, so this notification sounds from its own channel and opens
 * RingingActivity full screen. RingingService cancels it when it starts playing.
 */
internal object FallbackRingNotification {
    fun show(context: Context, launch: Intent, shiftName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        RingingChannels.ensureCreated(context)
        val content = PendingIntent.getActivity(
            context,
            AlarmIntents.REQUEST_FALLBACK_CONTENT,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, RingingChannels.FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_shift)
            .setContentTitle(shiftName)
            .setContentText("Пора на смену")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(content)
            .setFullScreenIntent(content, true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), android.media.AudioManager.STREAM_ALARM)
            .setVibrate(longArrayOf(0, 600, 200, 600, 200, 800))
            .setAutoCancel(true)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        try {
            manager.notify(AlarmIntents.NOTIFICATION_FALLBACK_RING, notification)
        } catch (error: SecurityException) {
            Log.e("ShiftAlarm", "Fallback ring notification was rejected", error)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(AlarmIntents.NOTIFICATION_FALLBACK_RING)
    }

}
