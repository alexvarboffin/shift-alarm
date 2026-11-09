package com.walhalla.shiftalarm.feature.calendar

import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.ui.UiText
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.model.ShiftTypeOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

data class CalendarDayUi(
    val date: LocalDate,
    val shiftName: String?,
    val shiftKind: ShiftKind?,
    val colorLight: Long?,
    val colorDark: Long?,
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
    val colorLight: Long?,
    val colorDark: Long?,
    val hasOverride: Boolean,
    val alarmEnabled: Boolean,
    val startTime: LocalTime?,
    val alarmLeadMinutes: Int?,
    val customAlarmTime: LocalDateTime?,
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
    /** Reading failed. Cleared only by [CalendarEvent.Retry]. */
    val loadError: UiText? = null,
    /** A write failed. Cleared by [CalendarEvent.DismissError] or [CalendarEvent.Retry]. */
    val commandError: UiText? = null,
)
