package com.walhalla.shiftalarm.core.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.NextAlarmPlanner
import com.walhalla.shiftalarm.core.model.NextTriggerSelector
import com.walhalla.shiftalarm.core.model.ScheduledTrigger
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class AlarmManagerAlarmScheduler(
    private val context: Context,
    private val shiftRepository: ShiftRepository,
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val directBootStore: DeviceProtectedAlarmStore,
) : AlarmScheduler {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun scheduleNext(nowEpochMillis: Long) {
        if (!context.isUserUnlocked()) {
            scheduleFromDirectBoot(nowEpochMillis)
            return
        }
        migrateDirectBootSnapshot()
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
        val pattern = shiftRepository.observeActivePattern().first()
        val overrides = shiftRepository.observeOverrides().first()
        val exceptions = alarmRepository.observeExceptions().first()
        val settings = settingsRepository.settings.first()

        var snoozeEpoch = settings.pendingSnoozeAtEpochMillis
        var snoozeName = settings.pendingSnoozeShiftName
        if (snoozeEpoch != null && snoozeEpoch <= nowEpochMillis) {
            settingsRepository.setPendingSnooze(null, "")
            snoozeEpoch = null
            snoozeName = ""
        }

        val selected = chooseTrigger(
            now = now,
            pattern = pattern,
            overrides = overrides,
            exceptions = exceptions,
            snoozeEpochMillis = snoozeEpoch,
            snoozeShiftName = snoozeName,
        )
        val snapshot = DirectBootSnapshot(
            triggerEpochMillis = selected?.epochMillis,
            shiftName = selected?.shiftName.orEmpty(),
            snooze = selected?.snooze == true,
            snoozeEpochMillis = snoozeEpoch,
            snoozeShiftName = snoozeName,
            pattern = pattern,
            overrides = overrides,
            exceptions = exceptions,
        )
        arm(selected, snapshot)
    }

    override suspend fun cancelNext() {
        if (context.isUserUnlocked()) {
            settingsRepository.setPendingSnooze(null, "")
        }
        alarmManager.cancel(firePending())
        val current = directBootStore.read()
        if (current != null) {
            directBootStore.write(
                current.copy(
                    triggerEpochMillis = null,
                    shiftName = "",
                    snooze = false,
                    snoozeEpochMillis = null,
                    snoozeShiftName = "",
                ),
            )
        }
    }

    override suspend fun snooze(minutes: Int, shiftName: String) {
        val delayMillis = minutes.coerceIn(1, 120) * 60_000L
        val at = System.currentTimeMillis() + delayMillis
        if (!context.isUserUnlocked()) {
            val current = directBootStore.read() ?: DirectBootSnapshot(
                triggerEpochMillis = at,
                shiftName = shiftName,
                snooze = true,
                snoozeEpochMillis = at,
                snoozeShiftName = shiftName,
                pattern = null,
                overrides = emptyList(),
                exceptions = emptyList(),
            )
            arm(
                ScheduledTrigger(at, shiftName, snooze = true),
                current.copy(
                    triggerEpochMillis = at,
                    shiftName = shiftName,
                    snooze = true,
                    snoozeEpochMillis = at,
                    snoozeShiftName = shiftName,
                ),
            )
            return
        }
        settingsRepository.setPendingSnooze(epochMillis = at, shiftName = shiftName)
        scheduleNext()
    }

    override suspend fun dismiss() {
        if (context.isUserUnlocked()) {
            settingsRepository.setPendingSnooze(null, "")
        } else {
            val current = directBootStore.read()
            if (current != null) {
                directBootStore.write(
                    current.copy(snoozeEpochMillis = null, snoozeShiftName = "", snooze = false),
                )
            }
        }
        scheduleNext()
    }

    override fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    override fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun firePending(selected: ScheduledTrigger? = null): PendingIntent {
        val intent = Intent(context, AlarmFireReceiver::class.java).setAction(AlarmIntents.ACTION_FIRE)
        if (selected != null) {
            intent.putExtra(AlarmIntents.EXTRA_TRIGGER_AT_MILLIS, selected.epochMillis)
            intent.putExtra(AlarmIntents.EXTRA_SHIFT_NAME, selected.shiftName)
            intent.putExtra(AlarmIntents.EXTRA_SNOOZE, selected.snooze)
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmIntents.REQUEST_FIRE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPending(): PendingIntent {
        val launch = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(context.packageName, MAIN_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            AlarmIntents.REQUEST_SHOW,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun chooseTrigger(
        now: java.time.ZonedDateTime,
        pattern: ShiftPattern?,
        overrides: List<DayOverride>,
        exceptions: List<AlarmException>,
        snoozeEpochMillis: Long?,
        snoozeShiftName: String,
    ): ScheduledTrigger? {
        val planned = pattern?.let {
            NextAlarmPlanner.earliest(
                now = now,
                pattern = it,
                overrides = overrides.associateBy { override -> override.date },
                alarmExceptions = exceptions.associateBy { exception -> exception.date },
            )
        }
        return NextTriggerSelector.select(
            nowEpochMillis = now.toInstant().toEpochMilli(),
            snoozeEpochMillis = snoozeEpochMillis,
            snoozeShiftName = snoozeShiftName,
            planned = planned,
        )
    }

    private suspend fun scheduleFromDirectBoot(nowEpochMillis: Long) {
        val snapshot = directBootStore.read()
        if (snapshot == null) {
            alarmManager.cancel(firePending())
            return
        }
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
        var snoozeEpoch = snapshot.snoozeEpochMillis
        var snoozeName = snapshot.snoozeShiftName
        if (snoozeEpoch != null && snoozeEpoch <= nowEpochMillis) {
            snoozeEpoch = null
            snoozeName = ""
        }
        val selected = chooseTrigger(
            now = now,
            pattern = snapshot.pattern,
            overrides = snapshot.overrides,
            exceptions = snapshot.exceptions,
            snoozeEpochMillis = snoozeEpoch,
            snoozeShiftName = snoozeName,
        )
        arm(
            selected,
            snapshot.copy(
                triggerEpochMillis = selected?.epochMillis,
                shiftName = selected?.shiftName.orEmpty(),
                snooze = selected?.snooze == true,
                snoozeEpochMillis = snoozeEpoch,
                snoozeShiftName = snoozeName,
            ),
        )
    }

    private suspend fun migrateDirectBootSnapshot() {
        val snapshot = directBootStore.read() ?: return
        val current = shiftRepository.observeActivePattern().first()
        if (current == null && snapshot.pattern != null) {
            shiftRepository.saveAndActivate(snapshot.pattern)
            snapshot.overrides.forEach { shiftRepository.upsertOverride(it) }
            snapshot.exceptions.forEach { alarmRepository.upsertException(it) }
            if (snapshot.snoozeEpochMillis != null) {
                settingsRepository.setPendingSnooze(snapshot.snoozeEpochMillis, snapshot.snoozeShiftName)
            }
        } else if (snapshot.snoozeEpochMillis != null && snapshot.snoozeEpochMillis > System.currentTimeMillis()) {
            val settings = settingsRepository.settings.first()
            if (settings.pendingSnoozeAtEpochMillis != snapshot.snoozeEpochMillis) {
                settingsRepository.setPendingSnooze(snapshot.snoozeEpochMillis, snapshot.snoozeShiftName)
            }
        }
    }

    private suspend fun arm(selected: ScheduledTrigger?, snapshot: DirectBootSnapshot) {
        directBootStore.write(
            snapshot.copy(
                triggerEpochMillis = selected?.epochMillis,
                shiftName = selected?.shiftName.orEmpty(),
                snooze = selected?.snooze == true,
            ),
        )
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (selected == null) {
            alarmManager.cancel(firePending())
            notifications.cancel(AlarmIntents.NOTIFICATION_EXACT_DENIED)
            return
        }
        val operation = firePending(selected)
        if (canScheduleExactAlarms()) {
            notifications.cancel(AlarmIntents.NOTIFICATION_EXACT_DENIED)
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(selected.epochMillis, showPending()),
                    operation,
                )
                return
            } catch (error: SecurityException) {
                Log.e(TAG, "setAlarmClock was rejected", error)
            }
        } else {
            Log.w(TAG, "Exact alarms are off. Arming an inexact fallback for ${selected.epochMillis}.")
        }
        scheduleInexactFallback(selected.epochMillis, operation)
        notifyExactAlarmsDenied(notifications)
    }

    private fun scheduleInexactFallback(triggerAt: Long, operation: PendingIntent) {
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } catch (error: Exception) {
            Log.e(TAG, "setAndAllowWhileIdle failed, using setWindow", error)
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                INEXACT_WINDOW_MILLIS,
                operation,
            )
        }
    }

    private fun notifyExactAlarmsDenied(notifications: NotificationManager) {
        val channelId = "shift_alarm_exact"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notifications.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Точные будильники",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Предупреждение, если точный будильник недоступен"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notifications.createNotificationChannel(channel)
            }
        }
        val settings = AlarmPermissionIntents.requestExactAlarm(context)
        val content = settings?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(
                context,
                AlarmIntents.NOTIFICATION_EXACT_DENIED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_shift)
            .setContentTitle("Точные будильники выключены")
            .setContentText("Смена поставлена приблизительно и может прозвенеть с задержкой.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        try {
            notifications.notify(AlarmIntents.NOTIFICATION_EXACT_DENIED, notification)
        } catch (error: SecurityException) {
            Log.e(TAG, "Exact-alarm warning was not posted", error)
        }
    }

    private companion object {
        const val TAG: String = "ShiftAlarm"
        const val MAIN_ACTIVITY: String = "com.walhalla.shiftalarm.MainActivity"
        const val INEXACT_WINDOW_MILLIS: Long = 15 * 60_000L
    }
}

private fun Context.isUserUnlocked(): Boolean {
    val userManager = getSystemService(UserManager::class.java) ?: return true
    return userManager.isUserUnlocked
}
