package com.walhalla.shiftalarm.core.database

import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal fun ShiftType.toEntity(): ShiftTypeEntity = ShiftTypeEntity(
    id = id,
    kind = kind.name,
    name = name,
    colorArgb = colorArgb,
    startHour = startTime?.hour,
    startMinute = startTime?.minute,
    alarmLeadMinutes = alarmLeadMinutes,
)

internal fun ShiftTypeEntity.toDomain(): ShiftType = ShiftType(
    id = id,
    kind = ShiftKind.valueOf(kind),
    name = name,
    colorArgb = colorArgb,
    startTime = if (startHour != null && startMinute != null) {
        LocalTime.of(startHour, startMinute)
    } else {
        null
    },
    alarmLeadMinutes = alarmLeadMinutes,
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
    customEpochDay = customTime?.toLocalDate()?.toEpochDay(),
    customHour = customTime?.hour,
    customMinute = customTime?.minute,
)

internal fun AlarmExceptionEntity.toDomain(): AlarmException = AlarmException(
    date = LocalDate.ofEpochDay(epochDay),
    enabled = enabled,
    customTime = if (customEpochDay != null && customHour != null && customMinute != null) {
        LocalDateTime.of(LocalDate.ofEpochDay(customEpochDay), LocalTime.of(customHour, customMinute))
    } else {
        null
    },
)
