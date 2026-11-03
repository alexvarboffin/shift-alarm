package com.walhalla.shiftalarm.feature.calendar

import com.walhalla.shiftalarm.core.model.ShiftKind
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDayUi(
    val date: LocalDate,
    val shiftName: String?,
    val shiftKind: ShiftKind?,
    val colorArgb: Int?,
    val hasOverride: Boolean,
    val alarmLabel: String?,
)

data class CalendarUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDayUi> = emptyList(),
    val selected: CalendarDayUi? = null,
    val hasActivePattern: Boolean = false,
    val errorMessage: String? = null,
)
