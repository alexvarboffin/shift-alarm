package com.walhalla.shiftalarm.core.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.NextAlarmPlanner
import com.walhalla.shiftalarm.core.model.NextTriggerSelector
import com.walhalla.shiftalarm.core.model.ScheduledTrigger
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
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
    private val directBootStore: DeviceProtectedAlarmStore,
) : AlarmScheduler {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun scheduleNext(notBeforeEpochMillis: Long) {
        if (!context.isUserUnlocked()) {
            scheduleFromDirectBoot(notBeforeEpochMillis)
            return
        }
        migrateDirectBootSnapshot()
        val zone = ZoneId.systemDefault()
        val notBefore = Instant.ofEpochMilli(notBeforeEpochMillis).atZone(zone).toLocalDateTime()
        val pattern = shiftRepository.observeActivePattern().first()
        val overrides = shiftRepository.observeOverrides().first()
        val exceptions = alarmRepository.observeExceptions().first()
        val overrideMap = overrides.associateBy { it.date }
        val exceptionMap = exceptions.associateBy { it.date }
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
                overrides = overrideMap,
                alarmExceptions = exceptionMap,
            )
        }
        val selected = NextTriggerSelector.select(
            notBeforeEpochMillis = notBeforeEpochMillis,
            snoozeEpochMillis = snoozeEpoch,
            snoozeShiftName = snoozeName,
            planned = planned,
            zone = zone,
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
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            AlarmIntents.REQUEST_SHOW,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun scheduleFromDirectBoot(notBeforeEpochMillis: Long) {
        val snapshot = directBootStore.read()
        if (snapshot?.pattern == null && snapshot?.triggerEpochMillis == null && snapshot?.snoozeEpochMillis == null) {
            alarmManager.cancel(firePending())
            return
        }
        val stored = snapshot ?: return
        val zone = ZoneId.systemDefault()
        val notBefore = Instant.ofEpochMilli(notBeforeEpochMillis).atZone(zone).toLocalDateTime()
        var snoozeEpoch = stored.snoozeEpochMillis
        var snoozeName = stored.snoozeShiftName
        if (snoozeEpoch != null && snoozeEpoch <= notBeforeEpochMillis) {
            snoozeEpoch = null
            snoozeName = ""
        }
        val planned = stored.pattern?.let { pattern ->
            NextAlarmPlanner.nextShiftAlarm(
                now = notBefore,
                pattern = pattern,
                overrides = stored.overrides.associateBy { it.date },
                alarmExceptions = stored.exceptions.associateBy { it.date },
            )
        }
        val selected = NextTriggerSelector.select(
            notBeforeEpochMillis = notBeforeEpochMillis,
            snoozeEpochMillis = snoozeEpoch,
            snoozeShiftName = snoozeName,
            planned = planned,
            zone = zone,
        )
        arm(
            selected,
            stored.copy(
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

    private companion object {
        const val TAG: String = "ShiftAlarm"
    }
}

private fun Context.isUserUnlocked(): Boolean {
    val userManager = getSystemService(UserManager::class.java) ?: return true
    return userManager.isUserUnlocked
}
