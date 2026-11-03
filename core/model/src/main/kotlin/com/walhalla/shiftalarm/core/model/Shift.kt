package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ShiftKind {
    DAY,
    NIGHT,
    AFTER_NIGHT,
    OFF,
    CUSTOM,
}

/**
 * A single slot in a cycle. Builtins ([ShiftKind.DAY], [ShiftKind.NIGHT],
 * [ShiftKind.AFTER_NIGHT], [ShiftKind.OFF]) and custom types share this shape.
 * [alarmTime] is null when the slot must not ring (отсыпной, выходной).
 */
data class ShiftType(
    val id: String,
    val kind: ShiftKind,
    val name: String,
    val colorArgb: Int,
    val alarmTime: LocalTime?,
)

/** Stable id of a [ShiftType]. UI contracts use this instead of a raw string. */
@JvmInline
value class ShiftTypeId(val value: String)

/** Projection of a shift type for pickers. Does not replace [ShiftType] in storage. */
data class ShiftTypeOption(
    val id: ShiftTypeId,
    val name: String,
    val kind: ShiftKind,
    val colorArgb: Int,
    val alarmTime: LocalTime?,
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
    val alarmAt: LocalDateTime?,
)

/** Per-date replacement of whatever the active pattern would have said. */
data class DayOverride(
    val date: LocalDate,
    val shift: ShiftType,
)

/**
 * Per-date alarm exception.
 * [enabled] false suppresses the shift alarm.
 * [enabled] true with a non-null [time] replaces the shift alarm time.
 * [enabled] true with a null [time] keeps the shift alarm time.
 */
data class AlarmException(
    val date: LocalDate,
    val enabled: Boolean,
    val time: LocalTime?,
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
