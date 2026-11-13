package com.walhalla.shiftalarm.feature.alarm

import com.walhalla.shiftalarm.core.ui.UiText

sealed interface RingingUiState {
    data object Loading : RingingUiState

    data class Ringing(
        val shiftName: String,
        val triggerAtEpochMillis: Long,
        val snoozeMinutes: Int,
    ) : RingingUiState

    data class Error(val errorMessage: UiText) : RingingUiState
}
