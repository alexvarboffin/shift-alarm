package com.walhalla.shiftalarm.core.model

import java.time.LocalTime

object BuiltinShifts {
    const val DAY_ID: String = "builtin-day"
    const val NIGHT_ID: String = "builtin-night"
    const val AFTER_NIGHT_ID: String = "builtin-after-night"
    const val OFF_ID: String = "builtin-off"
    const val DUTY_ID: String = "builtin-duty"

    val day: ShiftType = ShiftType(
        id = DAY_ID,
        kind = ShiftKind.DAY,
        name = "День",
        colorLight = 0xFFA16207L,
        colorDark = 0xFFF4B000L,
        startTime = LocalTime.of(7, 0),
    )

    val night: ShiftType = ShiftType(
        id = NIGHT_ID,
        kind = ShiftKind.NIGHT,
        name = "Ночь",
        colorLight = 0xFF1E3A8AL,
        colorDark = 0xFF3B82F6L,
        startTime = LocalTime.of(19, 0),
    )

    /** Rest day after a night shift. */
    val afterNight: ShiftType = ShiftType(
        id = AFTER_NIGHT_ID,
        kind = ShiftKind.AFTER_NIGHT,
        name = "Отсыпной",
        colorLight = 0xFF0F766EL,
        colorDark = 0xFF0D9488L,
        startTime = null,
    )

    /** Day off. Same neutral gray in both themes; it is not the rest-after-night color. */
    val off: ShiftType = ShiftType(
        id = OFF_ID,
        kind = ShiftKind.OFF,
        name = "Выходной",
        colorLight = 0xFF6B7280L,
        colorDark = 0xFF6B7280L,
        startTime = null,
    )

    /** 24-hour duty used by the сутки-трое template. */
    val duty: ShiftType = ShiftType(
        id = DUTY_ID,
        kind = ShiftKind.NIGHT,
        name = "Сутки",
        colorLight = 0xFF312E81L,
        colorDark = 0xFF6366F1L,
        startTime = LocalTime.of(8, 0),
    )
}
