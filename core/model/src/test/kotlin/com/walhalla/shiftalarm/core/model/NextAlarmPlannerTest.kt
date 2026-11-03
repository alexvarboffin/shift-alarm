package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextAlarmPlannerTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun nightShiftAfterMidnightRingsThePreviousEvening() {
        val shift = ShiftType(
            id = "night-0030",
            kind = ShiftKind.NIGHT,
            name = "Ночь",
            colorArgb = 0,
            startTime = LocalTime.of(0, 30),
            alarmLeadMinutes = 90,
        )
        val alarm = ShiftAlarms.instant(LocalDate.of(2026, 6, 2), shift, berlin)
        assertEquals(LocalDate.of(2026, 6, 1), alarm?.toLocalDate())
        assertEquals(LocalTime.of(23, 0), alarm?.toLocalTime())
    }

    @Test
    fun springGapMovesTheLocalStartForward() {
        val shift = ShiftType(
            id = "gap",
            kind = ShiftKind.DAY,
            name = "День",
            colorArgb = 0,
            startTime = LocalTime.of(2, 30),
            alarmLeadMinutes = 0,
        )
        val alarm = ShiftAlarms.instant(LocalDate.of(2026, 3, 29), shift, berlin)
        assertEquals(LocalTime.of(3, 30), alarm?.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), alarm?.offset)
    }

    @Test
    fun fallOverlapKeepsTheEarlierOffset() {
        val shift = ShiftType(
            id = "overlap",
            kind = ShiftKind.NIGHT,
            name = "Ночь",
            colorArgb = 0,
            startTime = LocalTime.of(2, 30),
            alarmLeadMinutes = 0,
        )
        val alarm = ShiftAlarms.instant(LocalDate.of(2026, 10, 25), shift, berlin)
        assertEquals(LocalTime.of(2, 30), alarm?.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), alarm?.offset)
    }

    @Test
    fun earliestPicksTodaysAlarmOverTomorrowsEarlierStart() {
        val today = LocalDate.of(2026, 6, 1)
        val pattern = ShiftPattern(
            id = "two",
            name = "две",
            template = PatternTemplate.CUSTOM,
            startDate = today,
            cycle = listOf(
                ShiftType("early", ShiftKind.DAY, "Рано", 0, LocalTime.of(22, 0), alarmLeadMinutes = 0),
                ShiftType("midnight", ShiftKind.NIGHT, "Полночь", 0, LocalTime.of(0, 30), alarmLeadMinutes = 90),
            ),
        )
        val now = ZonedDateTime.of(today, LocalTime.of(21, 0), utc)
        val planned = NextAlarmPlanner.earliest(now, pattern)
        assertEquals(ZonedDateTime.of(today, LocalTime.of(22, 0), utc), planned?.triggerAt)
        assertEquals("Рано", planned?.shift?.name)
    }

    @Test
    fun earliestUsesPreviousEveningWhenTodaysAlarmAlreadyPassed() {
        val today = LocalDate.of(2026, 6, 1)
        val pattern = ShiftPattern(
            id = "two",
            name = "две",
            template = PatternTemplate.CUSTOM,
            startDate = today,
            cycle = listOf(
                ShiftType("early", ShiftKind.DAY, "Рано", 0, LocalTime.of(22, 0), alarmLeadMinutes = 0),
                ShiftType("midnight", ShiftKind.NIGHT, "Полночь", 0, LocalTime.of(0, 30), alarmLeadMinutes = 90),
            ),
        )
        val now = ZonedDateTime.of(today, LocalTime.of(22, 30), utc)
        val planned = NextAlarmPlanner.earliest(now, pattern)
        assertEquals(ZonedDateTime.of(today, LocalTime.of(23, 0), utc), planned?.triggerAt)
        assertEquals("Полночь", planned?.shift?.name)
    }

    @Test
    fun absoluteCustomTimeOverridesTheLead() {
        val day = LocalDate.of(2026, 1, 5)
        val pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 1, 1))
        val custom = LocalDateTime.of(2026, 1, 4, 21, 15)
        val planned = NextAlarmPlanner.earliest(
            now = ZonedDateTime.of(LocalDate.of(2026, 1, 4), LocalTime.of(20, 0), utc),
            pattern = pattern,
            alarmExceptions = mapOf(
                day to AlarmException(day, enabled = true, customTime = custom),
            ),
        )
        assertEquals(ZonedDateTime.of(custom, utc), planned?.triggerAt)
        assertEquals(day, planned?.date)
    }

    @Test
    fun silencedDayIsSkipped() {
        val pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 1, 1))
        val silenced = LocalDate.of(2026, 1, 5)
        val planned = NextAlarmPlanner.earliest(
            now = ZonedDateTime.of(LocalDate.of(2026, 1, 4), LocalTime.of(20, 0), utc),
            pattern = pattern,
            alarmExceptions = mapOf(silenced to AlarmException(silenced, enabled = false, customTime = null)),
        )
        assertTrue(planned?.date != silenced)
        assertEquals("Ночь", planned?.shift?.name)
    }

    @Test
    fun offOnlyPatternHasNoAlarm() {
        val offOnly = ShiftTemplates.custom("off", "Выходные", LocalDate.of(2026, 1, 1), listOf(BuiltinShifts.off))
        assertNull(
            NextAlarmPlanner.earliest(
                ZonedDateTime.of(LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT, utc),
                offOnly,
            ),
        )
    }
}

class NextTriggerSelectorTest {
    @Test
    fun futureSnoozeWinsOverThePlannedShift() {
        val planned = PlannedAlarm(
            triggerAt = ZonedDateTime.of(LocalDate.of(2026, 1, 5), LocalTime.of(7, 0), ZoneOffset.UTC),
            shift = BuiltinShifts.day,
            date = LocalDate.of(2026, 1, 5),
        )
        val selected = NextTriggerSelector.select(
            nowEpochMillis = 1_000,
            snoozeEpochMillis = 5_000,
            snoozeShiftName = "Ночь",
            planned = planned,
        )
        assertEquals(5_000L, selected?.epochMillis)
        assertEquals("Ночь", selected?.shiftName)
        assertTrue(selected?.snooze == true)
    }

    @Test
    fun snoozeAtOrBeforeNowFallsThroughToTheShift() {
        val planned = PlannedAlarm(
            triggerAt = ZonedDateTime.of(LocalDate.of(2026, 1, 5), LocalTime.of(7, 0), ZoneOffset.UTC),
            shift = BuiltinShifts.day,
            date = LocalDate.of(2026, 1, 5),
        )
        val cutoff = planned.triggerAt.toInstant().toEpochMilli() - 1
        val selected = NextTriggerSelector.select(
            nowEpochMillis = cutoff,
            snoozeEpochMillis = cutoff,
            snoozeShiftName = "Ночь",
            planned = planned,
        )
        assertEquals("День", selected?.shiftName)
        assertTrue(selected?.snooze == false)
    }
}
