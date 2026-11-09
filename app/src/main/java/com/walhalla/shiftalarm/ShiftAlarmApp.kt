package com.walhalla.shiftalarm

import android.app.Application
import com.walhalla.shiftalarm.core.alarm.AlarmRescheduleWatcher
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Koin starts here, not in an Activity: receivers, the ringing service and the widget
 * run without UI, including before the first unlock (direct boot).
 */
class ShiftAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ShiftAlarmApp)
            modules(shiftAlarmModules)
        }
        get<AlarmRescheduleWatcher>().start()
    }
}
