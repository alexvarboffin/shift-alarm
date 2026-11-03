package com.walhalla.shiftalarm.core.database

import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftType
import java.time.LocalDate
import java.time.LocalTime

internal fun ShiftType.toEntity(): ShiftTypeEntity = ShiftTypeEntity(
    id = id,
    kind = kind.name,
    name = name,
    colorArgb = colorArgb,
    alarmHour = alarmTime?.hour,
    alarmMinute = alarmTime?.minute,
)

internal fun ShiftTypeEntity.toDomain(): ShiftType = ShiftType(
    id = id,
    kind = ShiftKind.valueOf(kind),
    name = name,
    colorArgb = colorArgb,
    alarmTime = if (alarmHour != null && alarmMinute != null) {
        LocalTime.of(alarmHour, alarmMinute)
    } else {
        null
    },
)

internal fun ShiftPattern.toEntity(isActive: Boolean): PatternEntity = PatternEntity(
    id = id,
    name = name,
    template = template.name,
    startEpochDay = startDate.toEpochDay(),
    isActive = isActive,
)

internal fun mapPattern(graph: PatternWithSlots, types: Map<String, ShiftType>): ShiftPattern {
    val cycle = graph.slots
        .sortedBy { it.position }
        .mapNotNull { slot -> types[slot.shiftTypeId] }
    return ShiftPattern(
        id = graph.pattern.id,
        name = graph.pattern.name,
        template = PatternTemplate.valueOf(graph.pattern.template),
        startDate = LocalDate.ofEpochDay(graph.pattern.startEpochDay),
        cycle = cycle,
    )
}

internal fun DayOverride.toEntity(): DayOverrideEntity = DayOverrideEntity(
    epochDay = date.toEpochDay(),
    shiftTypeId = shift.id,
)

internal fun AlarmException.toEntity(): AlarmExceptionEntity = AlarmExceptionEntity(
    epochDay = date.toEpochDay(),
    enabled = enabled,
    hour = time?.hour,
    minute = time?.minute,
)

internal fun AlarmExceptionEntity.toDomain(): AlarmException = AlarmException(
    date = LocalDate.ofEpochDay(epochDay),
    enabled = enabled,
    time = if (hour != null && minute != null) LocalTime.of(hour, minute) else null,
)
