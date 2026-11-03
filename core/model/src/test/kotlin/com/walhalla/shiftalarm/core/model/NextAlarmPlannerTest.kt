package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextAlarmPlannerTest {
    private val start: LocalDate = LocalDate.of(2026, 1, 1)
    private val pattern: ShiftPattern = ShiftTemplates.twoTwo(start)

    @Test
    fun returnsLaterTodayWhenTheDayAlarmIsStillAhead() {
        val planned = NextAlarmPlanner.nextShiftAlarm(LocalDateTime.of(2026, 1, 1, 6, 0), pattern)
        assertEquals(LocalDateTime.of(2026, 1, 1, 7, 0), planned?.triggerAt)
        assertEquals("День", planned?.shift?.name)
    }

    @Test
    fun skipsADayAlarmThatAlreadyPassedAndUsesTheNextNight() {
        // 2/2 is one slot per day: day, then night on the following day.
        val planned = NextAlarmPlanner.nextShiftAlarm(LocalDateTime.of(2026, 1, 1, 8, 0), pattern)
        assertEquals(LocalDateTime.of(2026, 1, 2, 19, 0), planned?.triggerAt)
        assertEquals("Ночь", planned?.shift?.name)
    }

    @Test
    fun skipsRestDays() {
        // Jan 1 day, Jan 2 night, Jan 3 отсыпной, Jan 4 выходной, Jan 5 day.
        val planned = NextAlarmPlanner.nextShiftAlarm(LocalDateTime.of(2026, 1, 2, 20, 0), pattern)
        assertEquals(LocalDateTime.of(2026, 1, 5, 7, 0), planned?.triggerAt)
    }

    @Test
    fun anEqualTimestampIsNotTheNextAlarm() {
        val planned = NextAlarmPlanner.nextShiftAlarm(LocalDateTime.of(2026, 1, 1, 7, 0), pattern)
        assertEquals(LocalDateTime.of(2026, 1, 2, 19, 0), planned?.triggerAt)
    }

    @Test
    fun exceptionCanSilenceADay() {
        val silenced = mapOf(
            LocalDate.of(2026, 1, 5) to AlarmException(
                date = LocalDate.of(2026, 1, 5),
                enabled = false,
                time = null,
            ),
        )
        val planned = NextAlarmPlanner.nextShiftAlarm(
            now = LocalDateTime.of(2026, 1, 2, 20, 0),
            pattern = pattern,
            alarmExceptions = silenced,
        )
        assertEquals(LocalDateTime.of(2026, 1, 6, 19, 0), planned?.triggerAt)
    }

    @Test
    fun exceptionCanMoveTheAlarmEarlier() {
        val moved = mapOf(
            LocalDate.of(2026, 1, 5) to AlarmException(
                date = LocalDate.of(2026, 1, 5),
                enabled = true,
                time = LocalTime.of(5, 30),
            ),
        )
        val planned = NextAlarmPlanner.nextShiftAlarm(
            now = LocalDateTime.of(2026, 1, 2, 20, 0),
            pattern = pattern,
            alarmExceptions = moved,
        )
        assertEquals(LocalDateTime.of(2026, 1, 5, 5, 30), planned?.triggerAt)
    }

    @Test
    fun overrideCanRemoveTheAlarmForThatDate() {
        val overrides = mapOf(
            LocalDate.of(2026, 1, 5) to DayOverride(LocalDate.of(2026, 1, 5), BuiltinShifts.off),
        )
        val planned = NextAlarmPlanner.nextShiftAlarm(
            now = LocalDateTime.of(2026, 1, 2, 20, 0),
            pattern = pattern,
            overrides = overrides,
        )
        assertEquals(LocalDateTime.of(2026, 1, 6, 19, 0), planned?.triggerAt)
    }

    @Test
    fun offOnlyPatternHasNoAlarmInsideTheHorizon() {
        val offOnly = ShiftTemplates.custom(
            id = "off",
            name = "Только выходные",
            startDate = start,
            cycle = listOf(BuiltinShifts.off),
        )
        assertNull(NextAlarmPlanner.nextShiftAlarm(LocalDateTime.of(2026, 1, 1, 0, 0), offOnly))
    }

    @Test
    fun emptyCycleYieldsNull() {
        val empty = ShiftPattern("e", "e", PatternTemplate.CUSTOM, start, emptyList())
        assertNull(NextAlarmPlanner.nextShiftAlarm(LocalDateTime.of(2026, 1, 1, 0, 0), empty))
    }
}

class NextTriggerSelectorTest {
    @Test
    fun futureSnoozeWinsOverThePlannedShift() {
        val planned = PlannedAlarm(
            triggerAt = LocalDateTime.of(2026, 1, 5, 7, 0),
            shift = BuiltinShifts.day,
            date = LocalDate.of(2026, 1, 5),
        )
        val selected = NextTriggerSelector.select(
            notBeforeEpochMillis = 1_000,
            snoozeEpochMillis = 5_000,
            snoozeShiftName = "Ночь",
            planned = planned,
            zone = ZoneOffset.UTC,
        )
        assertEquals(5_000L, selected?.epochMillis)
        assertEquals("Ночь", selected?.shiftName)
        assertTrue(selected?.snooze == true)
    }

    @Test
    fun snoozeAtOrBeforeTheCutoffFallsThroughToTheShift() {
        val planned = PlannedAlarm(
            triggerAt = LocalDateTime.of(2026, 1, 5, 7, 0),
            shift = BuiltinShifts.day,
            date = LocalDate.of(2026, 1, 5),
        )
        val cutoff = planned.triggerAt.toInstant(ZoneOffset.UTC).toEpochMilli() - 1
        val selected = NextTriggerSelector.select(
            notBeforeEpochMillis = cutoff,
            snoozeEpochMillis = cutoff,
            snoozeShiftName = "Ночь",
            planned = planned,
            zone = ZoneOffset.UTC,
        )
        assertEquals("День", selected?.shiftName)
        assertTrue(selected?.snooze == false)
    }
}
