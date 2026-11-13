package com.walhalla.shiftalarm

import android.app.Application
import com.walhalla.shiftalarm.core.alarm.AlarmRescheduleWatcher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShiftAlarmApp : Application() {
    @Inject lateinit var alarmRescheduleWatcher: AlarmRescheduleWatcher

    override fun onCreate() {
        super.onCreate()
        alarmRescheduleWatcher.start()
    }
}
