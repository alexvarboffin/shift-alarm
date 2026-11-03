package com.walhalla.shiftalarm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.R
import com.walhalla.shiftalarm.core.alarm.AlarmScheduler
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.UserSettings
import com.walhalla.shiftalarm.core.model.keepAlive
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    scopeOverride: CoroutineScope?,
) : ViewModel() {
    private val workScope = scopeOverride ?: viewModelScope
    private val permissions = MutableStateFlow(readPermissions())
    private val readError = MutableStateFlow<UiText?>(null)

    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        alarmScheduler: AlarmScheduler,
        @ApplicationContext context: Context,
        clock: Clock,
    ) : this(settingsRepository, alarmScheduler, context, clock, null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings.keepAlive(UserSettings()) {
            readError.value = UiText.Res(R.string.settings_error_read)
        },
        permissions,
        readError,
    ) { settings, permission, error ->
        if (error != null) {
            SettingsUiState(
                isLoading = false,
                selectedSnooze = settings.snoozeMinutes,
                vibrationEnabled = settings.vibrationEnabled,
                useDynamicColor = settings.useDynamicColor,
                notificationPermission = NotificationPermission.Denied,
                exactAlarm = false,
                fullScreenIntent = false,
                errorMessage = error,
            )
        } else {
            SettingsUiState(
                isLoading = false,
                selectedSnooze = settings.snoozeMinutes,
                vibrationEnabled = settings.vibrationEnabled,
                useDynamicColor = settings.useDynamicColor,
                notificationPermission = permission.notifications,
                exactAlarm = permission.exact,
                fullScreenIntent = permission.fullScreen,
            )
        }
    }.stateIn(
        scope = workScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun refreshPermissions() {
        permissions.update { readPermissions() }
        workScope.launch { alarmScheduler.scheduleNext(clock.millis()) }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectSnooze -> {
                if (event.minutes !in SettingsUiState.SNOOZE_OPTIONS) return
                workScope.launch { settingsRepository.setSnoozeMinutes(event.minutes) }
            }
            is SettingsEvent.SetUseDynamicColor -> {
                workScope.launch { settingsRepository.setUseDynamicColor(event.enabled) }
            }
        }
    }

    fun setSnoozeMinutes(minutes: Int) {
        onEvent(SettingsEvent.SelectSnooze(minutes))
    }

    fun setVibrationEnabled(enabled: Boolean) {
        workScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    private fun readPermissions(): PermissionSnapshot = PermissionSnapshot(
        notifications = notificationPermission(),
        exact = alarmScheduler.canScheduleExactAlarms(),
        fullScreen = alarmScheduler.canUseFullScreenIntent(),
    )

    private fun notificationPermission(): NotificationPermission {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermission.NotRequired
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) NotificationPermission.Granted else NotificationPermission.Denied
    }

    private data class PermissionSnapshot(
        val notifications: NotificationPermission,
        val exact: Boolean,
        val fullScreen: Boolean,
    )
}
