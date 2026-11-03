package com.walhalla.shiftalarm.feature.schedule

import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import com.walhalla.shiftalarm.core.model.ShiftType
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
            assertEquals(UiText.Res(R.string.schedule_error_read), viewModel.uiState.value.errorMessage)
            repository.patterns.value = listOf(ShiftTemplates.twoTwo(LocalDate.of(2026, 4, 1)))
            assertEquals("2/2", viewModel.uiState.value.patterns.single().name)
            assertTrue(viewModel.uiState.value.errorMessage is UiText.Res)
        } finally {
            job.cancel()
            scope.cancel()
        }
    }
}

private class FlakyShiftRepository : ShiftRepository {
    val patterns = MutableStateFlow<List<ShiftPattern>>(emptyList())

    override fun observePatterns(): Flow<List<ShiftPattern>> = patterns

    override fun observeActivePattern(): Flow<ShiftPattern?> = flow {
        throw IllegalStateException("secret")
    }

    override fun observeShiftTypes(): Flow<List<ShiftType>> =
        patterns.map { list -> list.flatMap { it.cycle }.distinctBy { it.id } }

    override fun observeOverrides(): Flow<List<DayOverride>> = flowOf(emptyList())

    override suspend fun upsertPattern(pattern: ShiftPattern) = Unit
    override suspend fun setActivePattern(patternId: String?) = Unit
    override suspend fun saveAndActivate(pattern: ShiftPattern) = Unit
    override suspend fun upsertOverride(override: DayOverride) = Unit
    override suspend fun deleteOverride(date: LocalDate) = Unit
}
