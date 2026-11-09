package com.walhalla.shiftalarm.feature.calendar

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val calendarModule = module {
    viewModel { CalendarViewModel(get(), get(), get(), get(), get()) }
}
