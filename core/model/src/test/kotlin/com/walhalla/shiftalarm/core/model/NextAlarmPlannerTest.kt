package com.walhalla.shiftalarm.core.model

import java.time.Instant
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
            colorLight = 0L,
            colorDark = 0L,
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
            colorLight = 0L,
            colorDark = 0L,
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
            colorLight = 0L,
            colorDark = 0L,
            startTime = LocalTime.of(2, 30),
            alarmLeadMinutes = 0,
        )
        val alarm = ShiftAlarms.instant(LocalDate.of(2026, 10, 25), shift, berlin)
        assertEquals(LocalTime.of(2, 30), alarm?.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), alarm?.offset)
    }

    @Test
    fun fallBackOverlapDoesNotJumpADayWhenTheEarlierInstantHasPassed() {
        val day = LocalDate.of(2026, 10, 25)
        val shift = ShiftType(
            id = "overlap",
            kind = ShiftKind.NIGHT,
            name = "Ночь",
            colorLight = 0L,
            colorDark = 0L,
            startTime = LocalTime.of(2, 45),
            alarmLeadMinutes = 0,
        )
        val pattern = ShiftPattern(
            id = "one",
            name = "одна",
            template = PatternTemplate.CUSTOM,
            startDate = day,
            cycle = listOf(shift),
        )
        val earlier = ZonedDateTime.of(day, LocalTime.of(2, 45), berlin)
        val now = earlier.plusMinutes(30)
        val planned = NextAlarmPlanner.earliest(now, pattern)
        val expected = earlier.withLaterOffsetAtOverlap()
        assertEquals(day, planned?.triggerAt?.toLocalDate())
        assertEquals(LocalTime.of(2, 45), planned?.triggerAt?.toLocalTime())
        assertEquals(ZoneOffset.ofHours(1), planned?.triggerAt?.offset)
        assertEquals(expected, planned?.triggerAt)
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
                ShiftType("early", ShiftKind.DAY, "Рано", 0L, 0L, LocalTime.of(22, 0), alarmLeadMinutes = 0),
                ShiftType("midnight", ShiftKind.NIGHT, "Полночь", 0L, 0L, LocalTime.of(0, 30), alarmLeadMinutes = 90),
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
                ShiftType("early", ShiftKind.DAY, "Рано", 0L, 0L, LocalTime.of(22, 0), alarmLeadMinutes = 0),
                ShiftType("midnight", ShiftKind.NIGHT, "Полночь", 0L, 0L, LocalTime.of(0, 30), alarmLeadMinutes = 90),
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
    fun fallBackOverlapUsesTheFirstOccurrenceOnTheSameDay() {
        val fall = berlin.rules.nextTransition(Instant.parse("2026-10-01T00:00:00Z"))
        val day = fall.dateTimeBefore.toLocalDate()
        val pattern = oneAlarm(day, LocalTime.of(2, 30))
        val now = ZonedDateTime.of(day, LocalTime.of(1, 0), berlin)
        val planned = NextAlarmPlanner.earliest(now, pattern)
        assertEquals(day, planned?.triggerAt?.toLocalDate())
        assertEquals(LocalTime.of(2, 30), planned?.triggerAt?.toLocalTime())
        assertEquals(fall.offsetBefore, planned?.triggerAt?.offset)
        assertTrue(planned!!.triggerAt.toInstant().isAfter(now.toInstant()))
    }

    @Test
    fun fallBackOverlapDoesNotJumpADayWhenTheEarlierInstantHasPassed() {
        val fall = berlin.rules.nextTransition(Instant.parse("2026-10-01T00:00:00Z"))
        val day = fall.dateTimeAfter.toLocalDate()
        val now = fall.instant.plusSeconds(30 * 60).atZone(berlin)
        val alarm = LocalTime.of(2, 45)
        val earlier = LocalDateTime.of(day, alarm).atOffset(fall.offsetBefore).toInstant()
        assertTrue(earlier.isBefore(now.toInstant()))
        val planned = NextAlarmPlanner.earliest(now, oneAlarm(day, alarm))
        assertEquals(day, planned?.triggerAt?.toLocalDate())
        assertEquals(alarm, planned?.triggerAt?.toLocalTime())
        assertEquals(fall.offsetAfter, planned?.triggerAt?.offset)
        assertTrue(planned!!.triggerAt.toInstant().isAfter(now.toInstant()))
    }

    @Test
    fun springForwardGapIsTheNextAlarmOnTheSameDay() {
        val spring = berlin.rules.nextTransition(Instant.parse("2026-03-01T00:00:00Z"))
        val day = spring.dateTimeBefore.toLocalDate()
        val now = spring.instant.minusSeconds(60 * 60).atZone(berlin)
        val planned = NextAlarmPlanner.earliest(now, oneAlarm(day, LocalTime.of(2, 30)))
        assertEquals(day, planned?.triggerAt?.toLocalDate())
        assertEquals(LocalTime.of(3, 30), planned?.triggerAt?.toLocalTime())
        assertTrue(planned!!.triggerAt.toInstant().isAfter(now.toInstant()))
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

    private fun oneAlarm(day: LocalDate, start: LocalTime): ShiftPattern {
        val shift = ShiftType("early", ShiftKind.CUSTOM, "Ранняя", 0L, 0L, start, alarmLeadMinutes = 0)
        return ShiftTemplates.custom("one", "Один", day, listOf(shift))
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
