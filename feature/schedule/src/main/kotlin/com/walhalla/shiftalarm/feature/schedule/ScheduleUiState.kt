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
    /** Reading failed. Cleared only by [ScheduleEvent.Retry]. */
    val loadError: UiText? = null,
    /** A write (save, activate) failed. Cleared by [ScheduleEvent.DismissError] or [ScheduleEvent.Retry]. */
    val commandError: UiText? = null,
)

sealed interface ScheduleEvent {
    /** Clears errors and reads the schedule again. */
    data object Retry : ScheduleEvent

    /** Hides a failed write (save, activate). Load errors go away only through [Retry]. */
    data object DismissError : ScheduleEvent
}
