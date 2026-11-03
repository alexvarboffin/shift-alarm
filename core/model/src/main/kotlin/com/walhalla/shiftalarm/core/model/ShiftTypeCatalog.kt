package com.walhalla.shiftalarm.core.model

/**
 * Built-in types plus anything already stored. Stored rows with the same id win,
 * so a later edit of a built-in alarm time stays visible in pickers.
 */
object ShiftTypeCatalog {
    fun all(stored: List<ShiftType>): List<ShiftType> {
        val byId = LinkedHashMap<String, ShiftType>()
        listOf(
            BuiltinShifts.day,
            BuiltinShifts.night,
            BuiltinShifts.afterNight,
            BuiltinShifts.off,
            BuiltinShifts.duty,
        ).forEach { byId[it.id] = it }
        stored.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun find(stored: List<ShiftType>, id: ShiftTypeId): ShiftType? =
        all(stored).firstOrNull { it.id == id.value }

    fun options(stored: List<ShiftType>): List<ShiftTypeOption> =
        all(stored).map { it.toOption() }
}

fun ShiftType.toOption(): ShiftTypeOption = ShiftTypeOption(
    id = ShiftTypeId(id),
    name = name,
    kind = kind,
    colorArgb = colorArgb,
    startTime = startTime,
    alarmLeadMinutes = alarmLeadMinutes,
)
