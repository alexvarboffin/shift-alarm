package com.walhalla.shiftalarm.feature.schedule

import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelTest {
    @Test
    fun readFailureKeepsLaterPatternUpdates() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = FlakyShiftRepository()
        val clock = Clock.fixed(
            LocalDate.of(2026, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneOffset.UTC,
        )
        val viewModel = ScheduleViewModel(
            repository = repository,
            widgetRefresher = object : HomeWidgetRefresher {
                override suspend fun refresh() = Unit
            },
            clock = clock,
            scopeOverride = scope,
        )
        val job = scope.launch { viewModel.uiState.collect {} }
        try {
            assertEquals(UiText.Res(R.string.schedule_error_read), viewModel.uiState.value.loadError)
            repository.patterns.value = listOf(ShiftTemplates.twoTwo(LocalDate.of(2026, 4, 1)))
            assertEquals("2/2", viewModel.uiState.value.patterns.single().name)
            assertTrue(viewModel.uiState.value.loadError is UiText.Res)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun retryClearsReadErrorAndSubscribesAgain() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = FlakyShiftRepository()
        val viewModel = ScheduleViewModel(
            repository = repository,
            widgetRefresher = object : HomeWidgetRefresher {
                override suspend fun refresh() = Unit
            },
            clock = Clock.fixed(LocalDate.of(2026, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            scopeOverride = scope,
        )
        val job = scope.launch { viewModel.uiState.collect {} }
        try {
            assertEquals(UiText.Res(R.string.schedule_error_read), viewModel.uiState.value.loadError)

            viewModel.onEvent(ScheduleEvent.Retry)

            assertNull(viewModel.uiState.value.loadError)
            assertEquals(2, repository.activeReads)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun leadOutOfRangeIsResourceError() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ScheduleViewModel(
            repository = FlakyShiftRepository(),
            widgetRefresher = object : HomeWidgetRefresher {
                override suspend fun refresh() = Unit
            },
            clock = Clock.fixed(LocalDate.of(2026, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            scopeOverride = scope,
        )
        val job = scope.launch { viewModel.uiState.collect {} }
        try {
            val day = ShiftTypeId("day")
            viewModel.openCustomEditor()
            viewModel.onEditorEvent(PatternEditorEvent.AddSlot(day))
            viewModel.onEditorEvent(PatternEditorEvent.SetLeadMinutes(day, 2000))

            assertEquals(
                UiText.Res(R.string.schedule_error_lead_range, listOf(0, 1440)),
                viewModel.uiState.value.editor?.errorMessage,
            )
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun failedWriteShowsCommandErrorAndDismissHidesIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ScheduleViewModel(
            repository = FlakyShiftRepository(failWrites = true),
            widgetRefresher = object : HomeWidgetRefresher {
                override suspend fun refresh() = Unit
            },
            clock = Clock.fixed(LocalDate.of(2026, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            scopeOverride = scope,
        )
        val job = scope.launch { viewModel.uiState.collect {} }
        try {
            viewModel.onEvent(ScheduleEvent.Retry)
            assertNull(viewModel.uiState.value.commandError)

            viewModel.applyTemplate(PatternTemplate.TWO_TWO)
            assertEquals(UiText.Res(R.string.schedule_error_save), viewModel.uiState.value.commandError)

            viewModel.onEvent(ScheduleEvent.DismissError)
            assertNull(viewModel.uiState.value.commandError)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    @Test
    fun slotCountErrorsAreResources() {
        assertEquals(UiText.Res(R.string.schedule_error_slots_min), slotCountError(0))
        assertNull(slotCountError(1))
        assertNull(slotCountError(31))
        assertEquals(UiText.Res(R.string.schedule_error_slots_max, listOf(31)), slotCountError(32))
    }
}

private class FlakyShiftRepository(private val failWrites: Boolean = false) : ShiftRepository {
    val patterns = MutableStateFlow<List<ShiftPattern>>(emptyList())

    override fun observePatterns(): Flow<List<ShiftPattern>> = patterns

    var activeReads = 0
        private set

    /** First subscription fails, later ones work. */
    override fun observeActivePattern(): Flow<ShiftPattern?> = flow {
        activeReads++
        if (activeReads == 1) throw IllegalStateException("secret")
        emit(null)
    }

    override fun observeShiftTypes(): Flow<List<ShiftType>> =
        patterns.map { list -> list.flatMap { it.cycle }.distinctBy { it.id } }

    override fun observeOverrides(): Flow<List<DayOverride>> = flowOf(emptyList())

    override suspend fun upsertPattern(pattern: ShiftPattern) = Unit
    override suspend fun setActivePattern(patternId: String?) = Unit
    override suspend fun saveAndActivate(pattern: ShiftPattern) {
        if (failWrites) throw IllegalStateException("disk full")
    }
    override suspend fun upsertOverride(override: DayOverride) = Unit
    override suspend fun deleteOverride(date: LocalDate) = Unit
}
