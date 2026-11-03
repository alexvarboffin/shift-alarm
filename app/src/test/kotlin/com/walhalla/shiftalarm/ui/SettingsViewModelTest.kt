package com.walhalla.shiftalarm.ui

import com.walhalla.shiftalarm.R
import com.walhalla.shiftalarm.core.alarm.AlarmScheduler
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsViewModelTest {
    @Test
    fun readFailureDoesNotReportPermissionsGranted() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(
            settingsRepository = object : SettingsRepository {
                override val settings: Flow<UserSettings> = flow { throw IllegalStateException("disk") }
                override suspend fun setSnoozeMinutes(minutes: Int) = Unit
                override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
                override suspend fun setUseDynamicColor(enabled: Boolean) = Unit
                override suspend fun setPendingSnooze(epochMillis: Long?, shiftName: String) = Unit
            },
            alarmScheduler = GrantingScheduler(),
            context = RuntimeEnvironment.getApplication(),
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            scopeOverride = scope,
        )
        val job = scope.launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }
        try {
            val state = viewModel.uiState.value
            assertEquals(UiText.Res(R.string.settings_error_read), state.errorMessage)
            assertFalse(state.exactAlarm)
            assertFalse(state.canScheduleExactAlarms)
            assertFalse(state.fullScreenIntent)
            assertFalse(state.canUseFullScreenIntent)
            assertEquals(NotificationPermission.Denied, state.notificationPermission)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }
}

private class GrantingScheduler : AlarmScheduler {
    override suspend fun scheduleNext(nowEpochMillis: Long) = Unit
    override suspend fun cancelNext() = Unit
    override suspend fun snooze(minutes: Int, shiftName: String) = Unit
    override suspend fun dismiss() = Unit
    override fun canScheduleExactAlarms(): Boolean = true
    override fun canUseFullScreenIntent(): Boolean = true
}
