package com.walhalla.shiftalarm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.walhalla.shiftalarm.core.model.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Backed-up user settings store. */
val SettingsStore: Qualifier = named("settings")

/** Device-local permission counters store, never backed up. */
val PermissionsStore: Qualifier = named("permissions")

val dataStoreModule = module {
    single<DataStore<Preferences>>(SettingsStore) { androidContext().shiftAlarmSettingsDataStore }
    single<DataStore<Preferences>>(PermissionsStore) { androidContext().permissionsDataStore }
    single {
        DataStoreSettingsRepository(
            settingsStore = get(SettingsStore),
            permissionsStore = get(PermissionsStore),
        )
    } bind SettingsRepository::class
}
