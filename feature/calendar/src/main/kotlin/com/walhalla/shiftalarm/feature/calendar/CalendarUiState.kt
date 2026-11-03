package com.walhalla.shiftalarm.feature.calendar

import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.ui.UiText
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.model.ShiftTypeOption
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class CalendarDayUi(
    val date: LocalDate,
    val shiftName: String?,
    val shiftKind: ShiftKind?,
    val colorArgb: Int?,
    val hasOverride: Boolean,
    val alarmLabel: String?,
)

/**
 * Detail of the selected calendar day. Enough for an editor to send [CalendarEvent]
 * without reading the grid cell again.
 */
data class SelectedDayDetail(
    val date: LocalDate,
    val shiftTypeId: ShiftTypeId?,
    val shiftName: String?,
    val shiftKind: ShiftKind?,
    val colorArgb: Int?,
    val hasOverride: Boolean,
    val alarmEnabled: Boolean,
    val customAlarmTime: LocalTime?,
    val alarmLabel: String?,
    val availableTypes: List<ShiftTypeOption>,
)

data class CalendarUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDayUi> = emptyList(),
    val selected: CalendarDayUi? = null,
    val selectedDay: SelectedDayDetail? = null,
    val hasActivePattern: Boolean = false,
    val errorMessage: UiText? = null,
)
