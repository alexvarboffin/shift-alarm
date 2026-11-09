package com.walhalla.shiftalarm.feature.calendar

import androidx.lifecycle.SavedStateHandle
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import android.os.Looper
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.junit.Assert.assertNull
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import com.walhalla.shiftalarm.core.ui.UiText

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarSetOverrideTest {
    @Test
    fun noActivePatternIsAnEmptyMonth() {
        val viewModel = CalendarViewModel(
            RecordingShiftRepository(emptyList()),
            EmptyAlarmRepository(),
            RecordingRefresher(),
            SavedStateHandle(),
            Clock.systemDefaultZone(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        try {
            assertFalse(viewModel.uiState.value.hasActivePattern)
            assertTrue(viewModel.uiState.value.days.isEmpty())
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun savedMonthAndDayAreRestored() {
        val handle = SavedStateHandle(
            mapOf(KEY_MONTH to "2026-03", KEY_DAY to "2026-03-02"),
        )
        val pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 3, 1))
        val viewModel = CalendarViewModel(
            RecordingShiftRepository(emptyList(), pattern),
            EmptyAlarmRepository(),
            RecordingRefresher(),
            handle,
            Clock.systemDefaultZone(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        try {
            assertEquals(java.time.YearMonth.of(2026, 3), viewModel.uiState.value.month)
            assertEquals(LocalDate.of(2026, 3, 2), viewModel.uiState.value.selected?.date)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun setOverrideNullClearsTheDay() {
        val date = LocalDate.of(2026, 3, 4)
        val shifts = RecordingShiftRepository(listOf(DayOverride(date, BuiltinShifts.day)))
        val widget = RecordingRefresher()
        val viewModel = CalendarViewModel(
            shifts,
            EmptyAlarmRepository(),
            widget,
            SavedStateHandle(),
            Clock.systemDefaultZone(),
        )

        viewModel.setOverride(date, null as ShiftTypeId?)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(date), shifts.deleted)
        assertTrue(shifts.overrides.value.isEmpty())
        assertEquals(1, widget.refreshes)
    }

    @Test
    fun failedWriteIsDismissibleCommandError() {
        val date = LocalDate.of(2026, 3, 4)
        val viewModel = CalendarViewModel(
            RecordingShiftRepository(emptyList(), failWrites = true),
            EmptyAlarmRepository(),
            RecordingRefresher(),
            SavedStateHandle(),
            Clock.systemDefaultZone(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        try {
            viewModel.setOverride(date, BuiltinShifts.night)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(UiText.Res(R.string.calendar_error_save), viewModel.uiState.value.commandError)

            viewModel.onEvent(CalendarEvent.DismissError)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(viewModel.uiState.value.commandError)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun loadErrorStaysOnDismissAndClearsOnRetry() {
        val shifts = RecordingShiftRepository(emptyList(), failFirstOverrideReads = 1)
        val viewModel = CalendarViewModel(
            shifts,
            EmptyAlarmRepository(),
            RecordingRefresher(),
            SavedStateHandle(),
            Clock.systemDefaultZone(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        try {
            assertEquals(UiText.Res(R.string.calendar_error_load), viewModel.uiState.value.loadError)

            viewModel.onEvent(CalendarEvent.DismissError)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(UiText.Res(R.string.calendar_error_load), viewModel.uiState.value.loadError)

            viewModel.onEvent(CalendarEvent.Retry)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(viewModel.uiState.value.loadError)
            assertEquals(2, shifts.overrideReads)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun daysBeforeStartDateHaveNoShiftAndNoAlarm() {
        val start = LocalDate.of(2026, 11, 9)
        val zone = ZoneId.of("Europe/Moscow")
        val viewModel = CalendarViewModel(
            RecordingShiftRepository(emptyList(), ShiftTemplates.twoTwo(start)),
            EmptyAlarmRepository(),
            RecordingRefresher(),
            SavedStateHandle(),
            Clock.fixed(start.atStartOfDay(zone).toInstant(), zone),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        try {
            val days = viewModel.uiState.value.days
            val before = days.filter { it.date.isBefore(start) }
            assertEquals(8, before.size)
            assertTrue(before.all { it.shiftName == null && it.alarmLabel == null })
            assertEquals("День", days.first { it.date == start }.shiftName)

            viewModel.select(start.minusDays(1))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(viewModel.uiState.value.selectedDay?.shiftName)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun clearSelectionClosesTheDaySheet() {
        val start = LocalDate.of(2026, 11, 9)
        val saved = SavedStateHandle()
        val viewModel = CalendarViewModel(
            RecordingShiftRepository(emptyList(), ShiftTemplates.twoTwo(start)),
            EmptyAlarmRepository(),
            RecordingRefresher(),
            saved,
            Clock.fixed(start.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        try {
            viewModel.select(start)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(start, viewModel.uiState.value.selectedDay?.date)

            viewModel.onEvent(CalendarEvent.ClearSelection)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(viewModel.uiState.value.selected)
            assertNull(viewModel.uiState.value.selectedDay)
            assertNull(saved.get<String>(KEY_DAY))
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun springForwardDayShowsTheGapAdjustedRingTime() {
        val berlin = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 3, 29)
        val early = ShiftType("early", ShiftKind.CUSTOM, "Ранняя", 0L, 0L, LocalTime.of(2, 30), alarmLeadMinutes = 0)
        val pattern = ShiftTemplates.custom("gap", "Разрыв", day, listOf(early))
        val clock = Clock.fixed(day.atStartOfDay(berlin).toInstant(), berlin)
        val viewModel = CalendarViewModel(
            RecordingShiftRepository(emptyList(), pattern),
            EmptyAlarmRepository(),
            RecordingRefresher(),
            SavedStateHandle(),
            clock,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch { viewModel.uiState.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        try {
            val label = viewModel.uiState.value.days.first { it.date == day }.alarmLabel
            assertEquals("03:30", label)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }
}

private class RecordingShiftRepository(
    initialOverrides: List<DayOverride>,
    private val active: ShiftPattern? = null,
    private val failWrites: Boolean = false,
    private val failFirstOverrideReads: Int = 0,
) : ShiftRepository {
    var overrideReads: Int = 0
        private set
    val overrides = MutableStateFlow(initialOverrides)
    val deleted = mutableListOf<LocalDate>()

    override fun observePatterns(): Flow<List<ShiftPattern>> = MutableStateFlow(listOfNotNull(active))

    override fun observeActivePattern(): Flow<ShiftPattern?> = MutableStateFlow(active)

    override fun observeShiftTypes(): Flow<List<ShiftType>> = MutableStateFlow(emptyList())

    override fun observeOverrides(): Flow<List<DayOverride>> = flow {
        overrideReads++
        if (overrideReads <= failFirstOverrideReads) throw IllegalStateException("disk")
        emitAll(overrides)
    }

    override suspend fun upsertPattern(pattern: ShiftPattern) = Unit

    override suspend fun setActivePattern(patternId: String?) = Unit

    override suspend fun saveAndActivate(pattern: ShiftPattern) = Unit

    override suspend fun upsertOverride(override: DayOverride) {
        if (failWrites) throw IllegalStateException("disk full")
        overrides.value = overrides.value.filter { it.date != override.date } + override
    }

    override suspend fun deleteOverride(date: LocalDate) {
        deleted += date
        overrides.value = overrides.value.filter { it.date != date }
    }
}

private class EmptyAlarmRepository : AlarmRepository {
    override fun observeExceptions(): Flow<List<AlarmException>> = MutableStateFlow(emptyList())

    override suspend fun upsertException(exception: AlarmException) = Unit

    override suspend fun deleteException(date: LocalDate) = Unit
}

private class RecordingRefresher : HomeWidgetRefresher {
    var refreshes: Int = 0

    override suspend fun refresh() {
        refreshes += 1
    }
}
