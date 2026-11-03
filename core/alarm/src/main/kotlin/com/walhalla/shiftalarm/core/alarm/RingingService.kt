package com.walhalla.shiftalarm.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.walhalla.shiftalarm.core.model.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RingingService : Service() {
    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var ringingLaunchIntent: RingingLaunchIntent

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private var wakeLock: PowerManager.WakeLock? = null
    private var ringingShiftName: String = "Смена"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                scheduler.dismiss()
                stopRinging()
            }
            ACTION_SNOOZE -> scope.launch {
                val minutes = settingsRepository.settings.first().snoozeMinutes
                scheduler.snooze(minutes, ringingShiftName)
                stopRinging()
            }
            else -> startRinging(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startRinging(intent: Intent?) {
        val shiftName = intent?.getStringExtra(AlarmIntents.EXTRA_SHIFT_NAME).orEmpty().ifBlank { "Смена" }
        val triggerAt = intent?.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())
            ?: System.currentTimeMillis()
        val snooze = intent?.getBooleanExtra(AlarmIntents.EXTRA_SNOOZE, false) ?: false
        ringingShiftName = shiftName
        acquireWakeLock()
        enterForeground(buildNotification(shiftName, triggerAt, snooze, vibration = true))
        scope.launch {
            val vibration = settingsRepository.settings.first().vibrationEnabled
            val notification = buildNotification(shiftName, triggerAt, snooze, vibration)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopRinging() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        shiftName: String,
        triggerAt: Long,
        snooze: Boolean,
        vibration: Boolean,
    ): Notification {
        ensureChannel(vibration)
        val content = PendingIntent.getActivity(
            this,
            AlarmIntents.REQUEST_CONTENT,
            ringingLaunchIntent.create(shiftName, triggerAt, snooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = serviceAction(AlarmIntents.REQUEST_STOP, ACTION_STOP)
        val snoozeAction = serviceAction(AlarmIntents.REQUEST_SNOOZE, ACTION_SNOOZE)
        val title = if (snooze) "Повтор: $shiftName" else shiftName
        val text = if (snooze) "Отложенный сигнал смены" else "Пора на смену"
        return NotificationCompat.Builder(this, channelId(vibration))
            .setSmallIcon(R.drawable.ic_stat_shift)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(content)
            .setFullScreenIntent(content, true)
            .addAction(0, "Стоп", stop)
            .addAction(0, "Отложить", snoozeAction)
            .build()
    }

    private fun serviceAction(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, RingingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(vibration: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val id = channelId(vibration)
        if (manager.getNotificationChannel(id) != null) return
        val name = if (vibration) "Будильник смены" else "Будильник смены без вибрации"
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сигнал начала смены"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            val audio = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audio)
            enableVibration(vibration)
            if (vibration) {
                vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 800)
            }
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shiftalarm:ringing").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun channelId(vibration: Boolean): String =
        if (vibration) CHANNEL_VIBRATE else CHANNEL_QUIET

    companion object {
        const val ACTION_FIRE: String = "com.walhalla.shiftalarm.action.RINGING_FIRE"
        const val ACTION_STOP: String = "com.walhalla.shiftalarm.action.RINGING_STOP"
        const val ACTION_SNOOZE: String = "com.walhalla.shiftalarm.action.RINGING_SNOOZE"

        private const val NOTIFICATION_ID: Int = 7101
        private const val CHANNEL_VIBRATE: String = "shift_alarm_ring_vibrate"
        private const val CHANNEL_QUIET: String = "shift_alarm_ring_quiet"
        private const val WAKE_LOCK_TIMEOUT_MILLIS: Long = 10 * 60 * 1000L
    }
}
