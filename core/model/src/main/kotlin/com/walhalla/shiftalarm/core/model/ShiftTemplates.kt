package com.walhalla.shiftalarm.core.model

import java.time.LocalDate

/**
 * Preset cycles. 2/2 is the day-night-rest rotation (день, ночь, отсыпной, выходной).
 * 3/3 and 5/2 are daytime blocks. 1/3 is сутки through three: one 24h duty,
 * then отсыпной and two выходных.
 */
object ShiftTemplates {
    const val TWO_TWO_ID: String = "pattern-2-2"
    const val THREE_THREE_ID: String = "pattern-3-3"
    const val ONE_THREE_ID: String = "pattern-1-3"
    const val FIVE_TWO_ID: String = "pattern-5-2"

    fun twoTwo(startDate: LocalDate, id: String = TWO_TWO_ID): ShiftPattern = ShiftPattern(
        id = id,
        name = "2/2",
        template = PatternTemplate.TWO_TWO,
        startDate = startDate,
        cycle = listOf(
            BuiltinShifts.day,
            BuiltinShifts.night,
            BuiltinShifts.afterNight,
            BuiltinShifts.off,
        ),
    )

    fun threeThree(startDate: LocalDate, id: String = THREE_THREE_ID): ShiftPattern = ShiftPattern(
        id = id,
        name = "3/3",
        template = PatternTemplate.THREE_THREE,
        startDate = startDate,
        cycle = listOf(
            BuiltinShifts.day,
            BuiltinShifts.day,
            BuiltinShifts.day,
            BuiltinShifts.off,
            BuiltinShifts.off,
            BuiltinShifts.off,
        ),
    )

    fun oneThree(startDate: LocalDate, id: String = ONE_THREE_ID): ShiftPattern = ShiftPattern(
        id = id,
        name = "Сутки-трое",
        template = PatternTemplate.ONE_THREE,
        startDate = startDate,
        cycle = listOf(
            BuiltinShifts.duty,
            BuiltinShifts.afterNight,
            BuiltinShifts.off,
            BuiltinShifts.off,
        ),
    )

    fun fiveTwo(startDate: LocalDate, id: String = FIVE_TWO_ID): ShiftPattern = ShiftPattern(
        id = id,
        name = "5/2",
        template = PatternTemplate.FIVE_TWO,
        startDate = startDate,
        cycle = List(5) { BuiltinShifts.day } + List(2) { BuiltinShifts.off },
    )

    fun custom(
        id: String,
        name: String,
        startDate: LocalDate,
        cycle: List<ShiftType>,
    ): ShiftPattern {
        require(cycle.isNotEmpty()) { "A custom pattern needs at least one shift." }
        return ShiftPattern(
            id = id,
            name = name,
            template = PatternTemplate.CUSTOM,
            startDate = startDate,
            cycle = cycle,
        )
    }
}
