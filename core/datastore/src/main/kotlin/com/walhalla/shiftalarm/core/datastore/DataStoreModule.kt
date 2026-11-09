package com.walhalla.shiftalarm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.walhalla.shiftalarm.core.model.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val dataStoreModule = module {
    single<DataStore<Preferences>> { androidContext().shiftAlarmSettingsDataStore }
    single { DataStoreSettingsRepository(get()) } bind SettingsRepository::class
}
