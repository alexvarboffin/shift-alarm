package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PatternLimitsTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun acceptsOneAndThirtyOneSlots() {
        assertNull(PatternLimits.slotCountMessage(PatternLimits.MIN_SLOTS))
        assertNull(PatternLimits.slotCountMessage(PatternLimits.MAX_SLOTS))
        ShiftTemplates.custom("ok", "Ок", start, List(31) { BuiltinShifts.day })
    }

    @Test
    fun rejectsEmptyAndThirtyTwoSlots() {
        assertEquals(
            "В цикле должен быть хотя бы один слот.",
            PatternLimits.slotCountMessage(0),
        )
        assertEquals(
            "В цикле не больше 31 слота.",
            PatternLimits.slotCountMessage(32),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ShiftTemplates.custom("empty", "Пусто", start, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShiftTemplates.custom("long", "Длинный", start, List(32) { BuiltinShifts.off })
        }
    }
}
