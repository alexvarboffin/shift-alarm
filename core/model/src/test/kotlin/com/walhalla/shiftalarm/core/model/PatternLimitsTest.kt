package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class PatternLimitsTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun acceptsOneAndThirtyOneSlots() {
        ShiftTemplates.custom("ok", "Ок", start, List(31) { BuiltinShifts.day })
    }

    @Test
    fun rejectsEmptyAndThirtyTwoSlots() {
        assertThrows(IllegalArgumentException::class.java) {
            ShiftTemplates.custom("empty", "Пусто", start, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShiftTemplates.custom("long", "Длинный", start, List(32) { BuiltinShifts.off })
        }
    }
}
