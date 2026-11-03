package com.walhalla.shiftalarm.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ShiftRepository,
    private val widgetRefresher: HomeWidgetRefresher,
) : ViewModel() {
    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.observePatterns(),
        repository.observeActivePattern(),
    ) { patterns, active ->
        ScheduleUiState(
            isLoading = false,
            patterns = patterns.map { pattern ->
                PatternSummary(
                    id = pattern.id,
                    name = pattern.name,
                    template = pattern.template,
                    cycleNames = pattern.cycle.map { it.name },
                    startDate = pattern.startDate,
                )
            },
            activePatternId = active?.id,
        )
    }.catch { error ->
        emit(
            ScheduleUiState(
                isLoading = false,
                errorMessage = error.message ?: "Не удалось прочитать график",
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    fun applyTemplate(template: PatternTemplate) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val pattern = when (template) {
                PatternTemplate.TWO_TWO -> ShiftTemplates.twoTwo(today)
                PatternTemplate.THREE_THREE -> ShiftTemplates.threeThree(today)
                PatternTemplate.ONE_THREE -> ShiftTemplates.oneThree(today)
                PatternTemplate.FIVE_TWO -> ShiftTemplates.fiveTwo(today)
                PatternTemplate.CUSTOM -> return@launch
            }
            repository.upsertPattern(pattern)
            repository.setActivePattern(pattern.id)
            widgetRefresher.refresh()
        }
    }

    fun activate(patternId: String) {
        viewModelScope.launch {
            repository.setActivePattern(patternId)
            widgetRefresher.refresh()
        }
    }
}
