package com.walhalla.shiftalarm.ui

import com.walhalla.shiftalarm.core.ui.UiText

enum class NotificationPermission {
    Granted,
    Denied,
    NotRequired,
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val snoozeOptions: List<Int> = SNOOZE_OPTIONS,
    val selectedSnooze: Int = 10,
    val vibrationEnabled: Boolean = true,
    val useDynamicColor: Boolean = true,
    val notificationPermission: NotificationPermission = NotificationPermission.NotRequired,
    val exactAlarm: Boolean = true,
    val fullScreenIntent: Boolean = true,
    val errorMessage: UiText? = null,
) {
    val canScheduleExactAlarms: Boolean get() = exactAlarm
    val canUseFullScreenIntent: Boolean get() = fullScreenIntent

    companion object {
        val SNOOZE_OPTIONS: List<Int> = listOf(5, 10, 15, 20, 30)
    }
}

sealed interface SettingsEvent {
    data class SelectSnooze(val minutes: Int) : SettingsEvent

    data class SetUseDynamicColor(val enabled: Boolean) : SettingsEvent
}
