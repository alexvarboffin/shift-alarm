package com.walhalla.shiftalarm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.alarm.AlarmScheduler
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ViewModel() {
    private val permissions = MutableStateFlow(readPermissions())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        permissions,
    ) { settings, permission ->
        SettingsUiState(
            isLoading = false,
            selectedSnooze = settings.snoozeMinutes,
            vibrationEnabled = settings.vibrationEnabled,
            useDynamicColor = settings.useDynamicColor,
            notificationPermission = permission.notifications,
            exactAlarm = permission.exact,
            fullScreenIntent = permission.fullScreen,
        )
    }.catch { error ->
        emit(
            SettingsUiState(
                isLoading = false,
                errorMessage = UiText.Raw(error.message ?: "Настройки недоступны"),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun refreshPermissions() {
        permissions.update { readPermissions() }
        viewModelScope.launch { alarmScheduler.scheduleNext(clock.millis()) }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectSnooze -> {
                if (event.minutes !in SettingsUiState.SNOOZE_OPTIONS) return
                viewModelScope.launch { settingsRepository.setSnoozeMinutes(event.minutes) }
            }
            is SettingsEvent.SetUseDynamicColor -> {
                viewModelScope.launch { settingsRepository.setUseDynamicColor(event.enabled) }
            }
        }
    }

    fun setSnoozeMinutes(minutes: Int) {
        onEvent(SettingsEvent.SelectSnooze(minutes))
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
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
