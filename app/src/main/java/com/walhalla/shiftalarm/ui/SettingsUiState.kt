package com.walhalla.shiftalarm.ui

data class SettingsUiState(
    val isLoading: Boolean = true,
    val snoozeMinutes: Int = 10,
    val vibrationEnabled: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val canUseFullScreenIntent: Boolean = true,
    val errorMessage: String? = null,
)
