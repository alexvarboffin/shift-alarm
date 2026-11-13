package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure cycle math. No clock, no storage, no Android.
 * An override for [date] wins over the pattern slot.
 * [ResolvedShift.alarmAt] is the lead-adjusted start in [zone], not "date + startTime".
 */
object ShiftCalculator {
    fun shiftFor(
        date: LocalDate,
        pattern: ShiftPattern,
        overrides: Map<LocalDate, DayOverride> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ResolvedShift {
        require(pattern.cycle.isNotEmpty()) { "Shift pattern cycle must not be empty." }
        val override = overrides[date]
        val shift = if (override != null) {
            override.shift
        } else {
            val days = ChronoUnit.DAYS.between(pattern.startDate, date)
            val index = Math.floorMod(days, pattern.cycle.size.toLong()).toInt()
            pattern.cycle[index]
        }
        return ResolvedShift(
            date = date,
            shift = shift,
            source = if (override != null) ShiftSource.OVERRIDE else ShiftSource.PATTERN,
            alarmAt = ShiftAlarms.instant(date, shift, zone),
        )
    }
}
