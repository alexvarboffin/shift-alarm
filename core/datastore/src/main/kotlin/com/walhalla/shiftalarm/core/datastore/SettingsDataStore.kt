package com.walhalla.shiftalarm.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** User settings: `datastore/shift_alarm_settings.preferences_pb`. Goes into backup. */
internal val Context.shiftAlarmSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)

/**
 * Per-device permission dialog counters: `datastore/permissions.preferences_pb`.
 * Excluded from backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`):
 * a restored counter on a new device would show DENIED_PERMANENTLY before any dialog.
 */
internal val Context.permissionsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PERMISSIONS_DATASTORE_NAME,
)

internal const val SETTINGS_DATASTORE_NAME = "shift_alarm_settings"
internal const val PERMISSIONS_DATASTORE_NAME = "permissions"
