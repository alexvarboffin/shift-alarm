package com.walhalla.shiftalarm.feature.alarm

import com.walhalla.shiftalarm.core.alarm.RingingLaunchIntent
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val alarmFeatureModule = module {
    single { ActivityRingingLaunchIntent(androidContext()) } bind RingingLaunchIntent::class
    viewModel { RingingViewModel(get(), get()) }
}
