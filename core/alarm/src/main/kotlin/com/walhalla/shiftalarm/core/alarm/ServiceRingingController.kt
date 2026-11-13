package com.walhalla.shiftalarm.core.alarm

import android.content.Context
import android.content.Intent

class ServiceRingingController(
    private val context: Context,
) : RingingController {
    override fun stop() {
        context.startService(
            Intent(context, RingingService::class.java).setAction(RingingService.ACTION_STOP),
        )
    }

    override fun snooze() {
        context.startService(
            Intent(context, RingingService::class.java).setAction(RingingService.ACTION_SNOOZE),
        )
    }
}
