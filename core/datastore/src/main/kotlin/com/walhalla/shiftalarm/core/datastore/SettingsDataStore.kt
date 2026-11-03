package com.walhalla.shiftalarm.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.shiftAlarmSettingsDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(
    name = "shift_alarm_settings",
)
