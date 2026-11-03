package com.walhalla.shiftalarm.feature.calendar

import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import android.os.Looper
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarSetOverrideTest {
    @Test
    fun setOverrideNullClearsTheDay() {
        val date = LocalDate.of(2026, 3, 4)
        val shifts = RecordingShiftRepository(listOf(DayOverride(date, BuiltinShifts.day)))
        val widget = RecordingRefresher()
        val viewModel = CalendarViewModel(shifts, EmptyAlarmRepository(), widget)

        viewModel.setOverride(date, null as ShiftTypeId?)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(date), shifts.deleted)
        assertTrue(shifts.overrides.value.isEmpty())
        assertEquals(1, widget.refreshes)
    }
}

private class RecordingShiftRepository(
    initialOverrides: List<DayOverride>,
) : ShiftRepository {
    val overrides = MutableStateFlow(initialOverrides)
    val deleted = mutableListOf<LocalDate>()

    override fun observePatterns(): Flow<List<ShiftPattern>> = MutableStateFlow(emptyList())

    override fun observeActivePattern(): Flow<ShiftPattern?> = MutableStateFlow(null)

    override fun observeShiftTypes(): Flow<List<ShiftType>> = MutableStateFlow(emptyList())

    override fun observeOverrides(): Flow<List<DayOverride>> = overrides

    override suspend fun upsertPattern(pattern: ShiftPattern) = Unit

    override suspend fun setActivePattern(patternId: String?) = Unit

    override suspend fun saveAndActivate(pattern: ShiftPattern) = Unit

    override suspend fun upsertOverride(override: DayOverride) {
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
