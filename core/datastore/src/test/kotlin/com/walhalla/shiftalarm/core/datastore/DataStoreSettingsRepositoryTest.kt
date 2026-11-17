package com.walhalla.shiftalarm.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.walhalla.shiftalarm.core.model.AppPermission
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsStore: DataStore<Preferences>
    private lateinit var permissionsStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        settingsStore = store("shift_alarm_settings.preferences_pb")
        permissionsStore = store("permissions.preferences_pb")
        repository = DataStoreSettingsRepository(settingsStore, permissionsStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, name) })

    @Test
    fun markRequestedReturnsCountFromPermissionsStore() = runBlocking {
        assertEquals(1, repository.markRequested(AppPermission.NOTIFICATIONS))
        assertEquals(2, repository.markRequested(AppPermission.NOTIFICATIONS))
        assertEquals(1, repository.markRequested(AppPermission.EXACT_ALARM))

        assertEquals(
            mapOf(AppPermission.NOTIFICATIONS to 2, AppPermission.EXACT_ALARM to 1),
            repository.permissionRequestCounts.first(),
        )
        assertEquals(2, permissionsStore.data.first()[requestCountKey(AppPermission.NOTIFICATIONS)])
        assertTrue(settingsStore.data.first().asMap().isEmpty())
    }

    @Test
    fun settingsLiveOnlyInSettingsStore() = runBlocking {
        repository.setSnoozeMinutes(15)
        repository.setVibrationEnabled(false)

        val settings = repository.settings.first()
        assertEquals(15, settings.snoozeMinutes)
        assertEquals(false, settings.vibrationEnabled)
        assertTrue(permissionsStore.data.first().asMap().isEmpty())
    }
}
