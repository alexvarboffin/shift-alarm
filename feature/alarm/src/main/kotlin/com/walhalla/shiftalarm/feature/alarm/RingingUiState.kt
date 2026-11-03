package com.walhalla.shiftalarm.feature.alarm

data class RingingUiState(
    val shiftName: String = "",
    val triggerAtEpochMillis: Long = 0L,
    val snoozeMinutes: Int = 10,
)
