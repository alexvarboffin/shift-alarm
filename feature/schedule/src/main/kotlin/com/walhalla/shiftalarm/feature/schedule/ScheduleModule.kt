package com.walhalla.shiftalarm.feature.schedule

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val scheduleModule = module {
    viewModel { ScheduleViewModel(get(), get(), get()) }
}
