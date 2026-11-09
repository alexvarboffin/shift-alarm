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
import android.os.UserManager
import androidx.core.app.NotificationCompat
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.UserSettings
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RingingService : Service(), KoinComponent {
    private val scheduler: AlarmScheduler by inject()

    private val settingsRepository: SettingsRepository by inject()

    private val ringingLaunchIntent: RingingLaunchIntent by inject()

    private val clock: Clock by inject()

    private val scope: CoroutineScope by inject(ApplicationScope)

    private var wakeLock: PowerManager.WakeLock? = null
    private var ringingShiftName: String = "Смена"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                runThenStopRinging(::stopRinging) { scheduler.dismiss() }
            }
            ACTION_SNOOZE -> scope.launch {
                runThenStopRinging(::stopRinging) {
                    val minutes = resolveSnoozeMinutes(isUserUnlocked()) {
                        settingsRepository.settings.first().snoozeMinutes
                    }
                    scheduler.snooze(minutes, ringingShiftName.ifBlank { "Смена" })
                }
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
        val now = clock.millis()
        val triggerAt = intent?.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT_MILLIS, now) ?: now
        val snooze = intent?.getBooleanExtra(AlarmIntents.EXTRA_SNOOZE, false) ?: false
        ringingShiftName = shiftName
        acquireWakeLock()
        enterForeground(buildNotification(shiftName, triggerAt, snooze, vibration = true))
        scope.launch {
            val vibration = if (isUserUnlocked()) {
                runCatching { settingsRepository.settings.first().vibrationEnabled }.getOrDefault(true)
            } else {
                true
            }
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
        return NotificationCompat.Builder(this, ringingChannelId(vibration))
            .setSmallIcon(R.drawable.ic_stat_shift)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(content)
            .presentRinging(content, scheduler.canUseFullScreenIntent())
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
        val id = ringingChannelId(vibration)
        val existing = manager.getNotificationChannel(id)
        if (existing != null && existing.shouldVibrate() == vibration) return
        if (existing != null) manager.deleteNotificationChannel(id)
        val name = if (vibration) "Будильник смены" else "Будильник смены без вибрации"
        // IMPORTANCE_HIGH is the heads-up channel used when full-screen intent is denied.
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

    private fun isUserUnlocked(): Boolean {
        val userManager = getSystemService(UserManager::class.java) ?: return true
        return userManager.isUserUnlocked
    }

    companion object {
        const val ACTION_FIRE: String = "com.walhalla.shiftalarm.action.RINGING_FIRE"
        const val ACTION_STOP: String = "com.walhalla.shiftalarm.action.RINGING_STOP"
        const val ACTION_SNOOZE: String = "com.walhalla.shiftalarm.action.RINGING_SNOOZE"

        private const val NOTIFICATION_ID: Int = 7101
        private const val WAKE_LOCK_TIMEOUT_MILLIS: Long = 10 * 60 * 1000L
    }
}

/** Ringing notification channels. Settings reads them to see if the user switched the current one off. */
object RingingChannels {
    const val VIBRATE: String = "shift_alarm_ring_vibrate"
    const val QUIET: String = "shift_alarm_ring_quiet"
    val all: List<String> = listOf(VIBRATE, QUIET)

    fun idFor(vibration: Boolean): String = if (vibration) VIBRATE else QUIET
}

internal const val RINGING_CHANNEL_VIBRATE: String = RingingChannels.VIBRATE
internal const val RINGING_CHANNEL_QUIET: String = RingingChannels.QUIET

internal fun ringingChannelId(vibration: Boolean): String = RingingChannels.idFor(vibration)

/**
 * Before the first unlock the credential-encrypted settings store is unavailable.
 * Snooze minutes then stay at the default and are not read from that store.
 */
internal suspend fun resolveSnoozeMinutes(
    userUnlocked: Boolean,
    readSettings: suspend () -> Int,
): Int {
    if (!userUnlocked) return UserSettings.DEFAULT_SNOOZE_MINUTES
    return readSettings()
}

internal suspend fun runThenStopRinging(stop: () -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } finally {
        stop()
    }
}

/**
 * Full-screen intent when the app may use one. Otherwise a high-priority heads-up
 * on the IMPORTANCE_HIGH ringing channel, with no full-screen intent.
 */
internal fun NotificationCompat.Builder.presentRinging(
    content: PendingIntent,
    fullScreen: Boolean,
): NotificationCompat.Builder = apply {
    setPriority(NotificationCompat.PRIORITY_MAX)
    setCategory(NotificationCompat.CATEGORY_ALARM)
    if (fullScreen) {
        setFullScreenIntent(content, true)
    }
}
