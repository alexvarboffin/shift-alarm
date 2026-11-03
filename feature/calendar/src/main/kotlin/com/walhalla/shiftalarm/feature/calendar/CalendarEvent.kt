package com.walhalla.shiftalarm.feature.calendar

import com.walhalla.shiftalarm.core.model.ShiftTypeId
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Same actions as [CalendarViewModel]: [CalendarViewModel.setOverride],
 * [CalendarViewModel.clearOverride], [CalendarViewModel.setAlarmException],
 * [CalendarViewModel.clearAlarmException], [CalendarViewModel.showMonth].
 *
 * [SetOverride.shiftTypeId] null clears the override. [SetAlarmException.customTime]
 * null with [SetAlarmException.enabled] true keeps the shift's own alarm time.
 */
sealed interface CalendarEvent {
    data class SetOverride(
        val date: LocalDate,
        val shiftTypeId: ShiftTypeId?,
    ) : CalendarEvent

    data class ClearOverride(val date: LocalDate) : CalendarEvent

    data class SetAlarmException(
        val date: LocalDate,
        val enabled: Boolean,
        val customTime: LocalTime?,
    ) : CalendarEvent

    data class ClearAlarmException(val date: LocalDate) : CalendarEvent

    data class ShowMonth(val month: YearMonth) : CalendarEvent
}
