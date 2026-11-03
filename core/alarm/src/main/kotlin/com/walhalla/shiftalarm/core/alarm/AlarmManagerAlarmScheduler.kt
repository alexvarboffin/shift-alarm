package com.walhalla.shiftalarm.core.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.walhalla.shiftalarm.core.model.NextAlarmPlanner
import com.walhalla.shiftalarm.core.model.NextTriggerSelector
import com.walhalla.shiftalarm.core.model.ScheduledTrigger
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.AlarmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class AlarmManagerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shiftRepository: ShiftRepository,
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
) : AlarmScheduler {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun scheduleNext(notBeforeEpochMillis: Long) {
        val zone = ZoneId.systemDefault()
        val notBefore = Instant.ofEpochMilli(notBeforeEpochMillis).atZone(zone).toLocalDateTime()
        val pattern = shiftRepository.observeActivePattern().first()
        val overrides = shiftRepository.observeOverrides().first().associateBy { it.date }
        val exceptions = alarmRepository.observeExceptions().first().associateBy { it.date }
        val settings = settingsRepository.settings.first()

        var snoozeEpoch = settings.pendingSnoozeAtEpochMillis
        var snoozeName = settings.pendingSnoozeShiftName
        if (snoozeEpoch != null && snoozeEpoch <= notBeforeEpochMillis) {
            settingsRepository.setPendingSnooze(null, "")
            snoozeEpoch = null
            snoozeName = ""
        }

        val planned = if (pattern == null) {
            null
        } else {
            NextAlarmPlanner.nextShiftAlarm(
                now = notBefore,
                pattern = pattern,
                overrides = overrides,
                alarmExceptions = exceptions,
            )
        }
        val selected = NextTriggerSelector.select(
            notBeforeEpochMillis = notBeforeEpochMillis,
            snoozeEpochMillis = snoozeEpoch,
            snoozeShiftName = snoozeName,
            planned = planned,
            zone = zone,
        )
        if (selected == null) {
            alarmManager.cancel(firePending())
            return
        }
        if (!canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms are not allowed. Next trigger ${selected.epochMillis} stays unarmed.")
            return
        }
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(selected.epochMillis, showPending()),
                firePending(selected),
            )
        } catch (error: SecurityException) {
            Log.e(TAG, "setAlarmClock was rejected", error)
        }
    }

    override suspend fun cancelNext() {
        settingsRepository.setPendingSnooze(null, "")
        alarmManager.cancel(firePending())
    }

    override suspend fun snooze(minutes: Int, shiftName: String) {
        val delayMillis = minutes.coerceIn(1, 120) * 60_000L
        settingsRepository.setPendingSnooze(
            epochMillis = System.currentTimeMillis() + delayMillis,
            shiftName = shiftName,
        )
        scheduleNext()
    }

    override suspend fun dismiss() {
        settingsRepository.setPendingSnooze(null, "")
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
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            AlarmIntents.REQUEST_SHOW,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG: String = "ShiftAlarm"
    }
}
