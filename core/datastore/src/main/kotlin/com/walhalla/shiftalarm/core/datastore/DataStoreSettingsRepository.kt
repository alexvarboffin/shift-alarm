package com.walhalla.shiftalarm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: UserSettings.DEFAULT_SNOOZE_MINUTES,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            pendingSnoozeAtEpochMillis = prefs[Keys.SNOOZE_EPOCH],
            pendingSnoozeShiftName = prefs[Keys.SNOOZE_NAME] ?: "",
        )
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        val safe = minutes.coerceIn(1, 120)
        dataStore.edit { prefs -> prefs[Keys.SNOOZE_MINUTES] = safe }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.VIBRATION] = enabled }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setPendingSnooze(epochMillis: Long?, shiftName: String) {
        dataStore.edit { prefs ->
            if (epochMillis == null) {
                prefs.remove(Keys.SNOOZE_EPOCH)
                prefs.remove(Keys.SNOOZE_NAME)
            } else {
                prefs[Keys.SNOOZE_EPOCH] = epochMillis
                prefs[Keys.SNOOZE_NAME] = shiftName
            }
        }
    }

    private object Keys {
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val SNOOZE_EPOCH = longPreferencesKey("pending_snooze_epoch_millis")
        val SNOOZE_NAME = stringPreferencesKey("pending_snooze_shift_name")
    }
}
