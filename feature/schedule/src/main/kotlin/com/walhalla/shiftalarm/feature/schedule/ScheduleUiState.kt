package com.walhalla.shiftalarm.feature.schedule

import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.LocalDate

data class PatternSummary(
    val id: String,
    val name: String,
    val template: PatternTemplate,
    val cycleNames: List<String>,
    val startDate: LocalDate,
)

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val patterns: List<PatternSummary> = emptyList(),
    val activePatternId: String? = null,
    val editor: PatternEditorUiState? = null,
    val errorMessage: UiText? = null,
)
