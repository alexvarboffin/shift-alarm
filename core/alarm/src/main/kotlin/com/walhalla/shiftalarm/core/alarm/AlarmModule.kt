package com.walhalla.shiftalarm.core.alarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Process-wide scope for receivers and the ringing service. Outlives any screen. */
val ApplicationScope: Qualifier = named("ApplicationScope")

val alarmModule = module {
    single<CoroutineScope>(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { DeviceProtectedAlarmStore(androidContext()) }
    single { AlarmManagerAlarmScheduler(androidContext(), get(), get(), get(), get()) } bind AlarmScheduler::class
    single { ServiceRingingController(androidContext()) } bind RingingController::class
    single { AlarmRescheduleWatcher(get(), get(), get(), get(), get(ApplicationScope)) }
}
