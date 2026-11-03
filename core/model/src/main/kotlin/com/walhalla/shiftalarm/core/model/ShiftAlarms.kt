package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Single place that turns a shift date into an alarm instant.
 *
 * [ZonedDateTime.of] resolves the local start in [zone]: a spring gap moves forward
 * by the length of the gap, and a fall overlap keeps the earlier offset.
 * The lead is then subtracted on the timeline, so the alarm can be the previous day.
 */
object ShiftAlarms {
    fun instant(shiftDate: LocalDate, shift: ShiftType, zone: ZoneId): ZonedDateTime? {
        val start = shift.startTime ?: return null
        return ZonedDateTime.of(shiftDate, start, zone).minusMinutes(shift.alarmLeadMinutes.toLong())
    }

    fun exceptionInstant(customTime: LocalDateTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(customTime, zone)
}
