package com.walhalla.shiftalarm.core.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Rearms the next alarm when exact-alarm permission is granted or revoked. Not exported. */
class ExactAlarmPermissionReceiver : BroadcastReceiver(), KoinComponent {
    private val scheduler: AlarmScheduler by inject()

    private val clock: Clock by inject()

    private val scope: CoroutineScope by inject(ApplicationScope)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!isExactAlarmPermissionAction(action)) return
        val pending = goAsync()
        val notBefore = clock.millis()
        scope.launch {
            try {
                scheduler.scheduleNext(notBefore)
            } catch (error: Exception) {
                Log.e(TAG, "Reschedule after exact-alarm permission change failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG: String = "ShiftAlarm"
    }
}

internal fun isExactAlarmPermissionAction(action: String?): Boolean =
    action != null && action in EXACT_ALARM_PERMISSION_ACTIONS

internal val EXACT_ALARM_PERMISSION_ACTIONS: Set<String> = setOf(
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
)
