package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ShiftCalculatorTest {
    private val start: LocalDate = LocalDate.of(2026, 1, 5)
    private val twoTwo: ShiftPattern = ShiftTemplates.twoTwo(start)

    @Test
    fun startDateMapsToTheFirstSlot() {
        val resolved = ShiftCalculator.shiftFor(start, twoTwo)
        assertEquals(BuiltinShifts.day, resolved.shift)
        assertEquals(ShiftSource.PATTERN, resolved.source)
        assertEquals(LocalDateTime.of(start, LocalTime.of(7, 0)), resolved.alarmAt)
    }

    @Test
    fun twoTwoWalksDayNightRestOff() {
        assertEquals("День", shiftName(start))
        assertEquals("Ночь", shiftName(start.plusDays(1)))
        assertEquals("Отсыпной", shiftName(start.plusDays(2)))
        assertEquals("Выходной", shiftName(start.plusDays(3)))
        assertEquals("День", shiftName(start.plusDays(4)))
        assertNull(ShiftCalculator.shiftFor(start.plusDays(2), twoTwo).alarmAt)
        assertNull(ShiftCalculator.shiftFor(start.plusDays(3), twoTwo).alarmAt)
    }

    @Test
    fun datesBeforeStartStayOnTheCycle() {
        assertEquals("Выходной", shiftName(start.minusDays(1)))
        assertEquals("День", shiftName(start.minusDays(4)))
        assertEquals("Ночь", shiftName(start.minusDays(3)))
    }

    @Test
    fun overrideReplacesThePatternSlot() {
        val date = start
        val overrides = mapOf(date to DayOverride(date, BuiltinShifts.night))
        val resolved = ShiftCalculator.shiftFor(date, twoTwo, overrides)
        assertEquals(BuiltinShifts.night, resolved.shift)
        assertEquals(ShiftSource.OVERRIDE, resolved.source)
        assertEquals(LocalTime.of(19, 0), resolved.alarmAt?.toLocalTime())
    }

    @Test
    fun emptyCycleIsRejected() {
        val empty = ShiftPattern(
            id = "empty",
            name = "Пусто",
            template = PatternTemplate.CUSTOM,
            startDate = start,
            cycle = emptyList(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ShiftCalculator.shiftFor(start, empty)
        }
    }

    @Test
    fun oneThreeIsDutyThenThreeRestDays() {
        val pattern = ShiftTemplates.oneThree(start)
        assertEquals(4, pattern.cycle.size)
        assertEquals(listOf("Сутки", "Отсыпной", "Выходной", "Выходной"), pattern.cycle.map { it.name })
        val duty = ShiftCalculator.shiftFor(start, pattern)
        assertEquals(LocalTime.of(8, 0), duty.alarmAt?.toLocalTime())
        assertNull(ShiftCalculator.shiftFor(start.plusDays(1), pattern).alarmAt)
    }

    @Test
    fun threeThreeIsThreeDaysThenThreeOff() {
        val pattern = ShiftTemplates.threeThree(start)
        assertEquals(List(3) { "День" } + List(3) { "Выходной" }, names(pattern))
        assertEquals("Выходной", ShiftCalculator.shiftFor(start.plusDays(3), pattern).shift.name)
        assertEquals("День", ShiftCalculator.shiftFor(start.plusDays(6), pattern).shift.name)
    }

    @Test
    fun fiveTwoIsFiveDaysThenWeekendAnchoredToStart() {
        val pattern = ShiftTemplates.fiveTwo(start)
        assertEquals(List(5) { "День" } + List(2) { "Выходной" }, names(pattern))
        assertEquals("Выходной", ShiftCalculator.shiftFor(start.plusDays(5), pattern).shift.name)
        assertEquals("Выходной", ShiftCalculator.shiftFor(start.plusDays(6), pattern).shift.name)
        assertEquals("День", ShiftCalculator.shiftFor(start.plusDays(7), pattern).shift.name)
    }

    private fun shiftName(date: LocalDate): String = ShiftCalculator.shiftFor(date, twoTwo).shift.name

    private fun names(pattern: ShiftPattern): List<String> = pattern.cycle.map { it.name }
}
