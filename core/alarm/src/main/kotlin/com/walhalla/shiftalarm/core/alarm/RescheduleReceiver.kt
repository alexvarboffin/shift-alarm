package com.walhalla.shiftalarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Exported only so the system can deliver protected broadcasts.
 * Any other action is ignored: an explicit intent from another app can name this
 * component even though it cannot send these actions implicitly.
 *
 * [Intent.ACTION_USER_UNLOCKED] is not in the filter. The platform does not deliver
 * it to manifest receivers. [Intent.ACTION_BOOT_COMPLETED] arrives after unlock and
 * is the signal that credential storage is available again.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "Ignored unexpected action $action")
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.scheduleNext(clock.millis())
            } catch (error: Exception) {
                Log.e(TAG, "Reschedule failed for $action", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG: String = "ShiftAlarm"

        val ALLOWED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
