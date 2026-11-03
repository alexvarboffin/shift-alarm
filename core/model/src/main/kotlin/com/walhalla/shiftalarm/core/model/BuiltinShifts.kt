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
        colorArgb = 0xFFF4B000.toInt(),
        startTime = LocalTime.of(7, 0),
    )

    val night: ShiftType = ShiftType(
        id = NIGHT_ID,
        kind = ShiftKind.NIGHT,
        name = "Ночь",
        colorArgb = 0xFF1E3A8A.toInt(),
        startTime = LocalTime.of(19, 0),
    )

    val afterNight: ShiftType = ShiftType(
        id = AFTER_NIGHT_ID,
        kind = ShiftKind.AFTER_NIGHT,
        name = "Отсыпной",
        colorArgb = 0xFF0F766E.toInt(),
        startTime = null,
    )

    val off: ShiftType = ShiftType(
        id = OFF_ID,
        kind = ShiftKind.OFF,
        name = "Выходной",
        colorArgb = 0xFF6B7280.toInt(),
        startTime = null,
    )

    /** 24-hour duty used by the сутки-трое template. */
    val duty: ShiftType = ShiftType(
        id = DUTY_ID,
        kind = ShiftKind.NIGHT,
        name = "Сутки",
        colorArgb = 0xFF312E81.toInt(),
        startTime = LocalTime.of(8, 0),
    )
}
