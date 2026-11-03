package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.ZonedDateTime

data class PlannedAlarm(
    val triggerAt: ZonedDateTime,
    val shift: ShiftType,
    val date: LocalDate,
)

/**
 * The only next-alarm calculation. Scans about two cycle lengths from [now]'s date
 * and returns the earliest instant strictly after [now]. A shift that starts after
 * midnight can therefore ring on the previous evening.
 */
object NextAlarmPlanner {
    fun earliest(
        now: ZonedDateTime,
        pattern: ShiftPattern,
        overrides: Map<LocalDate, DayOverride> = emptyMap(),
        alarmExceptions: Map<LocalDate, AlarmException> = emptyMap(),
    ): PlannedAlarm? {
        if (pattern.cycle.isEmpty()) return null
        val horizonDays = (pattern.cycle.size * 2).coerceAtLeast(2)
        val zone = now.zone
        var best: PlannedAlarm? = null
        var date = now.toLocalDate()
        repeat(horizonDays) {
            val resolved = ShiftCalculator.shiftFor(date, pattern, overrides, zone)
            val trigger = triggerAt(resolved, alarmExceptions[date], zone)
            if (trigger != null && trigger.toInstant().isAfter(now.toInstant())) {
                val candidate = PlannedAlarm(triggerAt = trigger, shift = resolved.shift, date = date)
                val current = best
                if (current == null || trigger.toInstant().isBefore(current.triggerAt.toInstant())) {
                    best = candidate
                }
            }
            date = date.plusDays(1)
        }
        return best
    }

    private fun triggerAt(
        resolved: ResolvedShift,
        exception: AlarmException?,
        zone: java.time.ZoneId,
    ): ZonedDateTime? = when {
        exception?.enabled == false -> null
        exception?.customTime != null -> ShiftAlarms.exceptionInstant(exception.customTime, zone)
        else -> resolved.alarmAt
    }
}
