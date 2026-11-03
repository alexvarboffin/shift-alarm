package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ShiftKind {
    DAY,
    NIGHT,
    AFTER_NIGHT,
    OFF,
    CUSTOM,
}

/**
 * A single slot in a cycle. [startTime] is when the shift begins.
 * It is null for [ShiftKind.OFF] and [ShiftKind.AFTER_NIGHT], which do not ring.
 * The alarm fires [alarmLeadMinutes] before that start, so it can fall on the previous day.
 */
data class ShiftType(
    val id: String,
    val kind: ShiftKind,
    val name: String,
    /** Opaque `0xAARRGGBB` for the light theme. */
    val colorLight: Long,
    /** Opaque `0xAARRGGBB` for the dark theme. */
    val colorDark: Long,
    val startTime: LocalTime?,
    val alarmLeadMinutes: Int = DEFAULT_LEAD_MINUTES,
) {
    init {
        require(alarmLeadMinutes in MIN_LEAD_MINUTES..MAX_LEAD_MINUTES) {
            "alarmLeadMinutes must be $MIN_LEAD_MINUTES..$MAX_LEAD_MINUTES."
        }
    }

    companion object {
        const val MIN_LEAD_MINUTES: Int = 0
        const val MAX_LEAD_MINUTES: Int = 1440
        const val DEFAULT_LEAD_MINUTES: Int = 90
    }
}

/** Stable id of a [ShiftType]. UI contracts use this instead of a raw string. */
@JvmInline
value class ShiftTypeId(val value: String)

/** Projection of a shift type for pickers. Does not replace [ShiftType] in storage. */
data class ShiftTypeOption(
    val id: ShiftTypeId,
    val name: String,
    val kind: ShiftKind,
    val colorLight: Long,
    val colorDark: Long,
    val startTime: LocalTime?,
    val alarmLeadMinutes: Int,
)

enum class PatternTemplate {
    TWO_TWO,
    THREE_THREE,
    ONE_THREE,
    FIVE_TWO,
    CUSTOM,
}

/**
 * Ordered cycle plus the calendar day that maps to [cycle] index 0.
 * Dates before [startDate] stay on the same cycle via floor-mod.
 */
data class ShiftPattern(
    val id: String,
    val name: String,
    val template: PatternTemplate,
    val startDate: LocalDate,
    val cycle: List<ShiftType>,
)

enum class ShiftSource {
    PATTERN,
    OVERRIDE,
}

data class ResolvedShift(
    val date: LocalDate,
    val shift: ShiftType,
    val source: ShiftSource,
    val alarmAt: ZonedDateTime?,
)

/** Per-date replacement of whatever the active pattern would have said. */
data class DayOverride(
    val date: LocalDate,
    val shift: ShiftType,
)

/**
 * Per-date alarm exception.
 * [enabled] false suppresses the shift alarm.
 * [enabled] true with a non-null [customTime] replaces the alarm with that absolute local date-time.
 * [enabled] true with a null [customTime] keeps the shift's own lead-adjusted instant.
 */
data class AlarmException(
    val date: LocalDate,
    val enabled: Boolean,
    val customTime: LocalDateTime?,
)

data class UserSettings(
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val vibrationEnabled: Boolean = true,
    val useDynamicColor: Boolean = true,
    val pendingSnoozeAtEpochMillis: Long? = null,
    val pendingSnoozeShiftName: String = "",
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES: Int = 10
    }
}
