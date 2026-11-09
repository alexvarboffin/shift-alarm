package com.walhalla.shiftalarm.feature.calendar

import com.walhalla.shiftalarm.core.model.ShiftTypeId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Same actions as [CalendarViewModel]: [CalendarViewModel.setOverride],
 * [CalendarViewModel.clearOverride], [CalendarViewModel.setAlarmException],
 * [CalendarViewModel.clearAlarmException], [CalendarViewModel.showMonth].
 *
 * [SetOverride.shiftTypeId] null clears the override. [SetAlarmException.customTime]
 * is an absolute local date-time. Null with [SetAlarmException.enabled] true keeps the shift lead.
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
        val customTime: LocalDateTime?,
    ) : CalendarEvent

    data class ClearAlarmException(val date: LocalDate) : CalendarEvent

    data class ShowMonth(val month: YearMonth) : CalendarEvent

    /** Clears the error and reads the month again. */
    data object Retry : CalendarEvent
}
