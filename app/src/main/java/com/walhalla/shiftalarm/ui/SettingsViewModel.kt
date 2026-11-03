package com.walhalla.shiftalarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.alarm.AlarmScheduler
import com.walhalla.shiftalarm.core.model.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {
    private val permissions = MutableStateFlow(readPermissions())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        permissions,
    ) { settings, permission ->
        SettingsUiState(
            isLoading = false,
            snoozeMinutes = settings.snoozeMinutes,
            vibrationEnabled = settings.vibrationEnabled,
            canScheduleExactAlarms = permission.exact,
            canUseFullScreenIntent = permission.fullScreen,
        )
    }.catch { error ->
        emit(SettingsUiState(isLoading = false, errorMessage = error.message ?: "Настройки недоступны"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun refreshPermissions() {
        permissions.update { readPermissions() }
    }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSnoozeMinutes(minutes) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    private fun readPermissions(): PermissionSnapshot = PermissionSnapshot(
        exact = alarmScheduler.canScheduleExactAlarms(),
        fullScreen = alarmScheduler.canUseFullScreenIntent(),
    )

    private data class PermissionSnapshot(
        val exact: Boolean,
        val fullScreen: Boolean,
    )
}
