package com.walhalla.shiftalarm.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Redraws tomorrow's label when the zone or the clock changes. Not exported. */
class TomorrowTimeZoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isClockChangeAction(intent.action)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                TomorrowShiftWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
