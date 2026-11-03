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
            val trigger = upcoming(triggerAt(resolved, alarmExceptions[date], zone), now)
            if (trigger != null) {
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

    /**
     * Fall overlap: [ZonedDateTime.of] keeps the earlier offset. If that instant is
     * already at or before [now], the same local time with [ZonedDateTime.withLaterOffsetAtOverlap]
     * is still today. The next calendar day is used only when that one has passed too.
     */
    private fun upcoming(trigger: ZonedDateTime?, now: ZonedDateTime): ZonedDateTime? {
        if (trigger == null) return null
        if (trigger.toInstant().isAfter(now.toInstant())) return trigger
        val later = trigger.withLaterOffsetAtOverlap()
        return later.takeIf { it.toInstant().isAfter(now.toInstant()) }
    }
}
