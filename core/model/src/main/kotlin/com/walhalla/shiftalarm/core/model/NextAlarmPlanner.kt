package com.walhalla.shiftalarm.core.model

import java.time.LocalDateTime
import java.time.LocalTime

data class PlannedAlarm(
    val triggerAt: LocalDateTime,
    val shift: ShiftType,
    val date: java.time.LocalDate,
)

/**
 * Walks forward from [now] and returns the first shift alarm strictly after it.
 * Alarm exceptions can silence a day or replace its time. Empty cycles yield null.
 */
object NextAlarmPlanner {
    const val DEFAULT_HORIZON_DAYS: Int = 366

    fun nextShiftAlarm(
        now: LocalDateTime,
        pattern: ShiftPattern,
        overrides: Map<java.time.LocalDate, DayOverride> = emptyMap(),
        alarmExceptions: Map<java.time.LocalDate, AlarmException> = emptyMap(),
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): PlannedAlarm? {
        require(horizonDays > 0) { "Horizon must be positive." }
        if (pattern.cycle.isEmpty()) return null
        var date = now.toLocalDate()
        repeat(horizonDays) {
            val resolved = ShiftCalculator.shiftFor(date, pattern, overrides)
            val time = alarmTime(resolved, alarmExceptions[date])
            if (time != null) {
                val trigger = LocalDateTime.of(date, time)
                if (trigger.isAfter(now)) {
                    return PlannedAlarm(triggerAt = trigger, shift = resolved.shift, date = date)
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun alarmTime(resolved: ResolvedShift, exception: AlarmException?): LocalTime? = when {
        exception == null -> resolved.shift.alarmTime
        !exception.enabled -> null
        exception.time != null -> exception.time
        else -> resolved.shift.alarmTime
    }
}
