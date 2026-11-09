package com.walhalla.shiftalarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AlarmFireReceiver : BroadcastReceiver(), KoinComponent {
    private val scheduler: AlarmScheduler by inject()

    private val clock: Clock by inject()

    private val scope: CoroutineScope by inject(ApplicationScope)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_FIRE) {
            Log.w(TAG, "Ignored unexpected action ${intent.action}")
            return
        }
        val ringing = Intent(context, RingingService::class.java).apply {
            action = RingingService.ACTION_FIRE
            putExtras(intent)
        }
        try {
            ContextCompat.startForegroundService(context, ringing)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Ringing service did not start", error)
        }
        val now = clock.millis()
        val firedAt = intent.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT_MILLIS, now)
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.scheduleNext(nowEpochMillis = notBeforeAfterFire(firedAt, now))
            } catch (error: Exception) {
                Log.e(TAG, "Reschedule after fire failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG: String = "ShiftAlarm"
    }
}

/** The next alarm must not be placed at a trigger that is already in the past. */
internal fun notBeforeAfterFire(firedAt: Long, now: Long): Long = maxOf(firedAt, now)
