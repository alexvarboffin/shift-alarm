package com.walhalla.shiftalarm

import com.walhalla.shiftalarm.core.alarm.alarmModule
import com.walhalla.shiftalarm.core.database.databaseModule
import com.walhalla.shiftalarm.core.datastore.dataStoreModule
import com.walhalla.shiftalarm.feature.alarm.alarmFeatureModule
import com.walhalla.shiftalarm.feature.calendar.calendarModule
import com.walhalla.shiftalarm.feature.schedule.scheduleModule
import com.walhalla.shiftalarm.ui.SettingsViewModel
import com.walhalla.shiftalarm.widget.widgetModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { SettingsViewModel(get(), get(), androidContext()) }
}

/** The whole graph. One Koin module per Gradle module; :app only assembles them. */
val shiftAlarmModules: List<Module> = listOf(
    appModule,
    alarmModule,
    databaseModule,
    dataStoreModule,
    alarmFeatureModule,
    calendarModule,
    scheduleModule,
    widgetModule,
)
