package com.walhalla.shiftalarm.ui

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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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
        val viewModel = viewModel(FakeSettingsRepository(), GrantingScheduler(granted = true))
        collect(viewModel)

        val state = viewModel.uiState.value
        assertEquals(PermissionStatus.GRANTED, state.exactAlarmStatus)
        assertEquals(PermissionStatus.GRANTED, state.fullScreenIntentStatus)
        // sdk 28: no runtime notification permission.
        assertEquals(PermissionStatus.GRANTED, state.notificationStatus)
    }

    @Test
    fun specialAccessAskedAndMissingGoesToSettings() {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository, GrantingScheduler(granted = false))
        collect(viewModel)
        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.exactAlarmStatus)

        viewModel.onEvent(SettingsEvent.PermissionRequested(AppPermission.EXACT_ALARM))

        assertEquals(setOf(AppPermission.EXACT_ALARM), repository.requested.value)
        assertEquals(PermissionStatus.DENIED_PERMANENTLY, viewModel.uiState.value.exactAlarmStatus)
        assertEquals(PermissionStatus.NOT_ASKED, viewModel.uiState.value.fullScreenIntentStatus)
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
    fun permissionStatusTable() {
        assertEquals(PermissionStatus.GRANTED, permissionStatus(granted = true, requested = true, rationale = false))
        assertEquals(PermissionStatus.NOT_ASKED, permissionStatus(granted = false, requested = false, rationale = false))
        assertEquals(PermissionStatus.CAN_ASK_AGAIN, permissionStatus(granted = false, requested = true, rationale = true))
        assertEquals(
            PermissionStatus.DENIED_PERMANENTLY,
            permissionStatus(granted = false, requested = true, rationale = false),
        )
    }

    private fun viewModel(repository: SettingsRepository, scheduler: AlarmScheduler) = SettingsViewModel(
        settingsRepository = repository,
        alarmScheduler = scheduler,
        context = RuntimeEnvironment.getApplication(),
        clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        scopeOverride = scope,
    )

    private fun collect(viewModel: SettingsViewModel) {
        scope.launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }
    }
}

private class FakeSettingsRepository(
    private val failFirstReads: Int = 0,
    private val failWrites: Boolean = false,
) : SettingsRepository {
    var settingsReads = 0
        private set
    val requested = MutableStateFlow<Set<AppPermission>>(emptySet())

    override val settings: Flow<UserSettings> = flow {
        settingsReads++
        if (settingsReads <= failFirstReads) throw IllegalStateException("disk")
        emit(UserSettings(snoozeMinutes = 15))
    }
    override val requestedPermissions: Flow<Set<AppPermission>> = requested

    override suspend fun markRequested(permission: AppPermission) {
        if (failWrites) throw IllegalStateException("disk")
        requested.value = requested.value + permission
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
