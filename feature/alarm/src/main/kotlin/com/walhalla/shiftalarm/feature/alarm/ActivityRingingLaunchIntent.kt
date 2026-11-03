package com.walhalla.shiftalarm.feature.alarm

import android.content.Context
import android.content.Intent
import com.walhalla.shiftalarm.core.alarm.AlarmIntents
import com.walhalla.shiftalarm.core.alarm.RingingLaunchIntent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRingingLaunchIntent @Inject constructor(
    @ApplicationContext private val context: Context,
) : RingingLaunchIntent {
    override fun create(shiftName: String, triggerAtEpochMillis: Long, snooze: Boolean): Intent =
        Intent(context, RingingActivity::class.java).apply {
            putExtra(AlarmIntents.EXTRA_SHIFT_NAME, shiftName)
            putExtra(AlarmIntents.EXTRA_TRIGGER_AT_MILLIS, triggerAtEpochMillis)
            putExtra(AlarmIntents.EXTRA_SNOOZE, snooze)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RingingBindings {
    @Binds
    abstract fun bindLaunchIntent(impl: ActivityRingingLaunchIntent): RingingLaunchIntent
}
