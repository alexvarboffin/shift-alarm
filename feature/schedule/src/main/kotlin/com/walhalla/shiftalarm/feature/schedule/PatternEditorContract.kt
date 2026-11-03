package com.walhalla.shiftalarm.feature.schedule

import com.walhalla.shiftalarm.core.model.PatternLimits
import com.walhalla.shiftalarm.core.ui.UiText
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.model.ShiftTypeOption
import java.time.LocalDate
import java.time.LocalTime

/**
 * Minimal custom-cycle editor. No screen yet: [ScheduleViewModel] keeps this state
 * and applies [PatternEditorEvent]. A cycle is valid at [PatternLimits.MIN_SLOTS]
 * through [PatternLimits.MAX_SLOTS] slots.
 */
data class PatternEditorUiState(
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val slots: List<ShiftTypeId> = emptyList(),
    val availableTypes: List<ShiftTypeOption> = emptyList(),
    val errorMessage: UiText? = null,
)

sealed interface PatternEditorEvent {
    data class AddSlot(val shiftTypeId: ShiftTypeId) : PatternEditorEvent

    data class RemoveSlot(val index: Int) : PatternEditorEvent

    data class MoveSlot(val fromIndex: Int, val toIndex: Int) : PatternEditorEvent

    data class SetName(val name: String) : PatternEditorEvent

    data class SetStartDate(val date: LocalDate) : PatternEditorEvent

    data class SetStartTime(
        val shiftTypeId: ShiftTypeId,
        val startTime: LocalTime?,
    ) : PatternEditorEvent

    data class SetLeadMinutes(
        val shiftTypeId: ShiftTypeId,
        val minutes: Int,
    ) : PatternEditorEvent

    data object Save : PatternEditorEvent
}
