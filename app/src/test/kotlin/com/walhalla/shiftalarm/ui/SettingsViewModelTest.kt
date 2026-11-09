package com.walhalla.shiftalarm.ui

import android.app.Application
import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.NotificationChannel
import android.app.NotificationManager
import com.walhalla.shiftalarm.core.alarm.RingingChannels
import com.walhalla.shiftalarm.R
import com.walhalla.shiftalarm.core.alarm.AlarmScheduler
import com.walhalla.shiftalarm.core.model.AppPermission
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.UserSettings
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Plain Application: ShiftAlarmApp would start Koin again for every test.
@Config(sdk = [28], application = Application::class)
class SettingsViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultStateGrantsNothing() {
        val state = SettingsUiState()
        assertEquals(PermissionStatus.NOT_ASKED, state.notificationStatus)
        assertEquals(PermissionStatus.NOT_ASKED, state.exactAlarmStatus)
        assertEquals(PermissionStatus.NOT_ASKED, state.fullScreenIntentStatus)
        assertFalse(state.exactAlarm)
        assertFalse(state.fullScreenIntent)
        assertEquals(NotificationPermission.Denied, state.notificationPermission)
    }

    @Test
    fun initialUiStateBeforeCollectionGrantsNothing() {
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true))
        val state = viewModel.uiState.value
        assertFalse(state.exactAlarm)
        assertFalse(state.fullScreenIntent)
        assertEquals(NotificationPermission.Denied, state.notificationPermission)
    }

    @Test
    fun readFailureDoesNotReportPermissionsGranted() {
        val repository = FakeSettingsRepository(failFirstReads = Int.MAX_VALUE)
        val viewModel = viewModel(repository, GrantingScheduler(granted = true))
        collect(viewModel)

        val state = viewModel.uiState.value
        assertEquals(UiText.Res(R.string.settings_error_read), state.errorMessage)
        assertFalse(state.exactAlarm)
        assertFalse(state.canScheduleExactAlarms)
        assertFalse(state.fullScreenIntent)
        assertFalse(state.canUseFullScreenIntent)
        assertEquals(NotificationPermission.Denied, state.notificationPermission)
    }

    @Test
    fun retryClearsErrorAndReadsAgain() {
        val repository = FakeSettingsRepository(failFirstReads = 1)
        val viewModel = viewModel(repository, GrantingScheduler(granted = true))
        collect(viewModel)
        assertEquals(UiText.Res(R.string.settings_error_read), viewModel.uiState.value.errorMessage)

        viewModel.onEvent(SettingsEvent.Retry)

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals(15, state.selectedSnooze)
        assertTrue(state.exactAlarm)
        assertEquals(2, repository.settingsReads)
    }

    @Test
    fun grantedOnlyAfterRealCheck() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(POST_NOTIFICATIONS)
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true))
        collect(viewModel)

        val state = viewModel.uiState.value
        assertEquals(PermissionStatus.GRANTED, state.exactAlarmStatus)
        assertEquals(PermissionStatus.GRANTED, state.fullScreenIntentStatus)
        assertEquals(PermissionStatus.GRANTED, state.notificationStatus)
    }

    @Test
    fun notificationsNotGrantedAndNeverAskedIsNotAsked() {
        // The test runtime is API 28: ContextCompat answers POST_NOTIFICATIONS from areNotificationsEnabled().
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true))
        collect(viewModel)

        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.notificationStatus)
    }

    @Test
    fun oldApiNotificationsOnAreNotRequired() {
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = false), sdkInt = 28)
        collect(viewModel)

        val state = viewModel.uiState.value
        assertEquals(PermissionStatus.NOT_REQUIRED, state.notificationStatus)
        assertEquals(PermissionStatus.NOT_REQUIRED, state.exactAlarmStatus)
        assertEquals(PermissionStatus.NOT_REQUIRED, state.fullScreenIntentStatus)
        assertTrue(state.exactAlarm)
        assertTrue(state.fullScreenIntent)
        assertEquals(NotificationPermission.NotRequired, state.notificationPermission)
    }

    @Test
    fun oldApiNotificationsOffGoToSettings() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true), sdkInt = 28)
        collect(viewModel)

        assertEquals(PermissionStatus.DENIED_PERMANENTLY, viewModel.uiState.value.notificationStatus)
    }

    @Test
    fun statusRulesByApiLevel() {
        assertEquals(PermissionStatus.NOT_REQUIRED, notificationStatus(32, enabled = true, granted = false, requestCount = 0, rationale = false))
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, notificationStatus(32, enabled = false, granted = false, requestCount = 0, rationale = false))
        assertEquals(PermissionStatus.NOT_ASKED, notificationStatus(33, enabled = true, granted = false, requestCount = 0, rationale = false))
        assertEquals(PermissionStatus.NOT_REQUIRED, specialAccessStatus(30, since = 31, granted = false, requestCount = 2))
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, specialAccessStatus(31, since = 31, granted = false, requestCount = 2))
        assertEquals(PermissionStatus.NOT_REQUIRED, specialAccessStatus(33, since = 34, granted = false, requestCount = 0))
        assertEquals(PermissionStatus.GRANTED, specialAccessStatus(34, since = 34, granted = true, requestCount = 0))
    }

    @Test
    fun specialAccessFirstRefusalCanAskAgainSecondGoesToSettings() {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository, GrantingScheduler(granted = false))
        collect(viewModel)
        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.exactAlarmStatus)

        viewModel.onEvent(SettingsEvent.PermissionRequested(AppPermission.EXACT_ALARM))

        // Counted, but the status changes only together with refreshPermissions.
        assertEquals(mapOf(AppPermission.EXACT_ALARM to 1), repository.counts.value)
        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.exactAlarmStatus)

        viewModel.refreshPermissions()
        assertEquals(PermissionStatus.CAN_ASK_AGAIN, viewModel.uiState.value.exactAlarmStatus)

        viewModel.onEvent(SettingsEvent.PermissionRequested(AppPermission.EXACT_ALARM))
        viewModel.refreshPermissions()
        assertEquals(mapOf(AppPermission.EXACT_ALARM to 2), repository.counts.value)
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, viewModel.uiState.value.exactAlarmStatus)
        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.fullScreenIntentStatus)
    }

    @Test
    fun storedCountsAreReadOnStart() {
        val repository = FakeSettingsRepository(initial = mapOf(AppPermission.FULL_SCREEN_INTENT to 1))
        val viewModel = viewModel(repository, GrantingScheduler(granted = false))
        collect(viewModel)

        assertEquals(PermissionStatus.CAN_ASK_AGAIN, viewModel.uiState.value.fullScreenIntentStatus)
        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.exactAlarmStatus)
    }

    @Test
    fun markRequestedFailureShowsWriteError() {
        val repository = FakeSettingsRepository(failWrites = true)
        val viewModel = viewModel(repository, GrantingScheduler(granted = false))
        collect(viewModel)

        viewModel.onEvent(SettingsEvent.PermissionRequested(AppPermission.NOTIFICATIONS))

        assertEquals(UiText.Res(R.string.settings_error_write), viewModel.uiState.value.errorMessage)
    }

    @Test
    fun ringingChannelOffIsDeniedAndPointsToTheChannel() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(RingingChannels.RING, "ring", NotificationManager.IMPORTANCE_NONE),
        )
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true), sdkInt = 28)
        collect(viewModel)

        val state = viewModel.uiState.value
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, state.notificationStatus)
        assertEquals(RingingChannels.RING, state.blockedChannelId)
    }

    @Test
    fun fallbackChannelOffIsDeniedAndPointsToIt() {
        val app = RuntimeEnvironment.getApplication()
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(RingingChannels.FALLBACK, "fallback", NotificationManager.IMPORTANCE_NONE),
        )
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true), sdkInt = 28)
        collect(viewModel)

        assertEquals(PermissionStatus.DENIED_PERMANENTLY, viewModel.uiState.value.notificationStatus)
        assertEquals(RingingChannels.FALLBACK, viewModel.uiState.value.blockedChannelId)
    }

    @Test
    fun bothChannelsOffPointsToRingFirst() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RingingChannels.RING, "ring", NotificationManager.IMPORTANCE_NONE))
        manager.createNotificationChannel(NotificationChannel(RingingChannels.FALLBACK, "fallback", NotificationManager.IMPORTANCE_NONE))
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = false), sdkInt = 28)
        collect(viewModel)

        assertEquals(RingingChannels.RING, viewModel.uiState.value.blockedChannelId)
    }

    @Test
    fun channelRuleOnEveryApi() {
        assertEquals(
            PermissionStatus.DENIED_PERMANENTLY,
            notificationStatus(34, enabled = true, granted = true, requestCount = 2, rationale = false, channelOff = true),
        )
        assertEquals(
            PermissionStatus.DENIED_PERMANENTLY,
            notificationStatus(34, enabled = false, granted = true, requestCount = 2, rationale = false),
        )
        assertEquals(
            PermissionStatus.NOT_ASKED,
            notificationStatus(34, enabled = false, granted = false, requestCount = 0, rationale = false, channelOff = true),
        )
        assertEquals(
            PermissionStatus.DENIED_PERMANENTLY,
            notificationStatus(28, enabled = true, granted = false, requestCount = 0, rationale = false, channelOff = true),
        )
    }

    @Test
    fun permissionStatusTable() {
        assertEquals(PermissionStatus.GRANTED, permissionStatus(granted = true, requestCount = 5, rationale = false))
        assertEquals(PermissionStatus.CAN_ASK_AGAIN, permissionStatus(granted = false, requestCount = 3, rationale = true))
        assertEquals(PermissionStatus.NOT_ASKED, permissionStatus(granted = false, requestCount = 0, rationale = false))
        assertEquals(PermissionStatus.CAN_ASK_AGAIN, permissionStatus(granted = false, requestCount = 1, rationale = false))
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, permissionStatus(granted = false, requestCount = 2, rationale = false))
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, permissionStatus(granted = false, requestCount = 7, rationale = false))
    }

    private fun viewModel(
        repository: SettingsRepository,
        scheduler: AlarmScheduler,
        sdkInt: Int = 34,
    ) = SettingsViewModel(
        settingsRepository = repository,
        alarmScheduler = scheduler,
        context = RuntimeEnvironment.getApplication(),
        clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        scopeOverride = scope,
        sdkInt = sdkInt,
    )

    private fun collect(viewModel: SettingsViewModel) {
        scope.launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }
    }
}

private class FakeSettingsRepository(
    private val failFirstReads: Int = 0,
    private val failWrites: Boolean = false,
    initial: Map<AppPermission, Int> = emptyMap(),
) : SettingsRepository {
    var settingsReads = 0
        private set
    val counts = MutableStateFlow(initial)

    override val settings: Flow<UserSettings> = flow {
        settingsReads++
        if (settingsReads <= failFirstReads) throw IllegalStateException("disk")
        emit(UserSettings(snoozeMinutes = 15))
    }
    override val permissionRequestCounts: Flow<Map<AppPermission, Int>> = counts

    override suspend fun markRequested(permission: AppPermission) {
        if (failWrites) throw IllegalStateException("disk")
        counts.value = counts.value + (permission to (counts.value[permission] ?: 0) + 1)
    }

    override suspend fun setSnoozeMinutes(minutes: Int) = Unit
    override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
    override suspend fun setUseDynamicColor(enabled: Boolean) = Unit
    override suspend fun setPendingSnooze(epochMillis: Long?, shiftName: String) = Unit
}

private class GrantingScheduler(private val granted: Boolean) : AlarmScheduler {
    override suspend fun scheduleNext(nowEpochMillis: Long) = Unit
    override suspend fun cancelNext() = Unit
    override suspend fun snooze(minutes: Int, shiftName: String) = Unit
    override suspend fun dismiss() = Unit
    override fun canScheduleExactAlarms(): Boolean = granted
    override fun canUseFullScreenIntent(): Boolean = granted
}
