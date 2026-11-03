package com.walhalla.shiftalarm.core.alarm

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceRingingController @Inject constructor(
    @ApplicationContext private val context: Context,
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
