package com.walhalla.shiftalarm.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.PatternLimits
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.ShiftTypeCatalog
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ShiftRepository,
    private val widgetRefresher: HomeWidgetRefresher,
    private val clock: Clock,
) : ViewModel() {
    private val editor = MutableStateFlow<EditorDraft?>(null)

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.observePatterns(),
        repository.observeActivePattern(),
        repository.observeShiftTypes(),
        editor,
    ) { patterns, active, storedTypes, draft ->
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
            editor = draft?.toState(storedTypes),
        )
    }.catch { error ->
        emit(
            ScheduleUiState(
                isLoading = false,
                errorMessage = UiText.Raw(error.message ?: "Не удалось прочитать график"),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    fun applyTemplate(template: PatternTemplate) {
        if (template == PatternTemplate.CUSTOM) {
            openCustomEditor()
            return
        }
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val pattern = when (template) {
                PatternTemplate.TWO_TWO -> ShiftTemplates.twoTwo(today)
                PatternTemplate.THREE_THREE -> ShiftTemplates.threeThree(today)
                PatternTemplate.ONE_THREE -> ShiftTemplates.oneThree(today)
                PatternTemplate.FIVE_TWO -> ShiftTemplates.fiveTwo(today)
                PatternTemplate.CUSTOM -> return@launch
            }
            repository.saveAndActivate(pattern)
            widgetRefresher.refresh()
        }
    }

    fun openCustomEditor() {
        editor.value = EditorDraft(
            name = "Свой график",
            startDate = LocalDate.now(clock),
            slots = emptyList(),
        )
    }

    fun onEditorEvent(event: PatternEditorEvent) {
        val current = editor.value ?: return
        when (event) {
            is PatternEditorEvent.SetName -> editor.value = current.copy(name = event.name, saveError = null)
            is PatternEditorEvent.SetStartDate -> editor.value = current.copy(startDate = event.date, saveError = null)
            is PatternEditorEvent.AddSlot -> {
                if (current.slots.size >= PatternLimits.MAX_SLOTS) {
                    editor.value = current.copy(saveError = PatternLimits.slotCountMessage(current.slots.size + 1))
                    return
                }
                editor.value = current.copy(slots = current.slots + event.shiftTypeId, saveError = null)
            }
            is PatternEditorEvent.RemoveSlot -> {
                if (event.index !in current.slots.indices) return
                editor.value = current.copy(
                    slots = current.slots.filterIndexed { index, _ -> index != event.index },
                    saveError = null,
                )
            }
            is PatternEditorEvent.MoveSlot -> editor.value = current.copy(
                slots = moveSlot(current.slots, event.fromIndex, event.toIndex),
                saveError = null,
            )
            is PatternEditorEvent.SetStartTime -> {
                val timing = current.timings[event.shiftTypeId] ?: TypeTiming()
                editor.value = current.copy(
                    timings = current.timings + (event.shiftTypeId to timing.copy(
                        startTime = event.startTime,
                        startTimeSet = true,
                    )),
                    saveError = null,
                )
            }
            is PatternEditorEvent.SetLeadMinutes -> {
                if (event.minutes !in ShiftType.MIN_LEAD_MINUTES..ShiftType.MAX_LEAD_MINUTES) {
                    editor.value = current.copy(saveError = "Упреждение от 0 до 1440 минут.")
                    return
                }
                val timing = current.timings[event.shiftTypeId] ?: TypeTiming()
                editor.value = current.copy(
                    timings = current.timings + (event.shiftTypeId to timing.copy(alarmLeadMinutes = event.minutes)),
                    saveError = null,
                )
            }
            PatternEditorEvent.Save -> viewModelScope.launch { save(current) }
            PatternEditorEvent.Cancel -> editor.value = null
        }
    }

    fun activate(patternId: String) {
        viewModelScope.launch {
            repository.setActivePattern(patternId)
            widgetRefresher.refresh()
        }
    }

    private suspend fun save(draft: EditorDraft) {
        if (PatternLimits.slotCountMessage(draft.slots.size) != null) return
        val stored = repository.observeShiftTypes().first()
        val cycle = draft.slots.mapIndexed { index, id ->
            val base = ShiftTypeCatalog.find(stored, id) ?: run {
                editor.update { it?.copy(saveError = "Неизвестный тип смены.") }
                return
            }
            draft.timings[id]?.applyTo(base, index) ?: base
        }
        val pattern = ShiftTemplates.custom(
            id = "custom-${UUID.randomUUID()}",
            name = draft.name.ifBlank { "Свой график" },
            startDate = draft.startDate,
            cycle = cycle,
        )
        repository.saveAndActivate(pattern)
        widgetRefresher.refresh()
        editor.value = null
    }

    private fun EditorDraft.toState(stored: List<ShiftType>): PatternEditorUiState =
        PatternEditorUiState(
            name = name,
            startDate = startDate,
            slots = slots,
            availableTypes = ShiftTypeCatalog.options(stored).map { option ->
                val timing = timings[option.id] ?: return@map option
                option.copy(
                    startTime = if (timing.startTimeSet) timing.startTime else option.startTime,
                    alarmLeadMinutes = timing.alarmLeadMinutes ?: option.alarmLeadMinutes,
                )
            },
            errorMessage = (PatternLimits.slotCountMessage(slots.size) ?: saveError)?.let(UiText::Raw),
        )

    private data class EditorDraft(
        val name: String,
        val startDate: LocalDate,
        val slots: List<ShiftTypeId>,
        val timings: Map<ShiftTypeId, TypeTiming> = emptyMap(),
        val saveError: String? = null,
    )

    private data class TypeTiming(
        val startTime: LocalTime? = null,
        val startTimeSet: Boolean = false,
        val alarmLeadMinutes: Int? = null,
    ) {
        fun applyTo(base: ShiftType, index: Int): ShiftType {
            val start = if (startTimeSet) startTime else base.startTime
            val lead = alarmLeadMinutes ?: base.alarmLeadMinutes
            if (start == base.startTime && lead == base.alarmLeadMinutes) return base
            return base.copy(
                id = "${base.id}@$index",
                startTime = start,
                alarmLeadMinutes = lead,
            )
        }
    }
}

private fun moveSlot(slots: List<ShiftTypeId>, fromIndex: Int, toIndex: Int): List<ShiftTypeId> {
    if (fromIndex !in slots.indices || toIndex !in slots.indices || fromIndex == toIndex) return slots
    val next = slots.toMutableList()
    val moved = next.removeAt(fromIndex)
    next.add(toIndex, moved)
    return next
}
