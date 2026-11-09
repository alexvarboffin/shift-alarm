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
import com.walhalla.shiftalarm.core.model.keepAlive
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel internal constructor(
    private val repository: ShiftRepository,
    private val widgetRefresher: HomeWidgetRefresher,
    private val clock: Clock,
    scopeOverride: CoroutineScope?,
) : ViewModel() {
    private val workScope = scopeOverride ?: viewModelScope
    private val editor = MutableStateFlow<EditorDraft?>(null)
    private val readError = MutableStateFlow<UiText?>(null)
    private val commandError = MutableStateFlow<UiText?>(null)
    private val reload = MutableStateFlow(0)

    @Inject
    constructor(
        repository: ShiftRepository,
        widgetRefresher: HomeWidgetRefresher,
        clock: Clock,
    ) : this(repository, widgetRefresher, clock, null)

    val uiState: StateFlow<ScheduleUiState> = combine(
        reload.flatMapLatest { repository.observePatterns().keepAlive(emptyList()) { readError.value = readFailure() } },
        reload.flatMapLatest { repository.observeActivePattern().keepAlive(null) { readError.value = readFailure() } },
        reload.flatMapLatest { repository.observeShiftTypes().keepAlive(emptyList()) { readError.value = readFailure() } },
        editor,
        combine(commandError, readError) { command, read -> command ?: read },
    ) { patterns, active, storedTypes, draft, error ->
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
            errorMessage = error,
        )
    }.stateIn(
        scope = workScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    private fun readFailure(): UiText = UiText.Res(R.string.schedule_error_read)

    fun onEvent(event: ScheduleEvent) {
        when (event) {
            ScheduleEvent.Retry -> retry()
            ScheduleEvent.DismissError -> commandError.value = null
        }
    }

    /** "Retry": clears both errors and subscribes to the repository again. */
    fun retry() {
        commandError.value = null
        readError.value = null
        reload.update { it + 1 }
    }

    /** Runs a write. A failure becomes a command error instead of crashing the screen. */
    private fun command(block: suspend () -> Unit) {
        workScope.launch {
            try {
                block()
                commandError.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                commandError.value = UiText.Res(R.string.schedule_error_save)
            }
        }
    }

    fun applyTemplate(template: PatternTemplate) {
        if (template == PatternTemplate.CUSTOM) {
            openCustomEditor()
            return
        }
        command {
            val today = LocalDate.now(clock)
            val pattern = when (template) {
                PatternTemplate.TWO_TWO -> ShiftTemplates.twoTwo(today)
                PatternTemplate.THREE_THREE -> ShiftTemplates.threeThree(today)
                PatternTemplate.ONE_THREE -> ShiftTemplates.oneThree(today)
                PatternTemplate.FIVE_TWO -> ShiftTemplates.fiveTwo(today)
                PatternTemplate.CUSTOM -> return@command
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
                    editor.value = current.copy(saveError = slotCountError(current.slots.size + 1))
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
                    editor.value = current.copy(
                        saveError = UiText.Res(
                            R.string.schedule_error_lead_range,
                            listOf(ShiftType.MIN_LEAD_MINUTES, ShiftType.MAX_LEAD_MINUTES),
                        ),
                    )
                    return
                }
                val timing = current.timings[event.shiftTypeId] ?: TypeTiming()
                editor.value = current.copy(
                    timings = current.timings + (event.shiftTypeId to timing.copy(alarmLeadMinutes = event.minutes)),
                    saveError = null,
                )
            }
            PatternEditorEvent.Save -> command { save(current) }
            PatternEditorEvent.Cancel -> editor.value = null
        }
    }

    fun activate(patternId: String) {
        command {
            repository.setActivePattern(patternId)
            widgetRefresher.refresh()
        }
    }

    private suspend fun save(draft: EditorDraft) {
        if (slotCountError(draft.slots.size) != null) return
        val stored = repository.observeShiftTypes().first()
        val cycle = draft.slots.mapIndexed { index, id ->
            val base = ShiftTypeCatalog.find(stored, id) ?: run {
                editor.update { it?.copy(saveError = UiText.Res(R.string.schedule_error_unknown_type)) }
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
            errorMessage = slotCountError(slots.size) ?: saveError,
        )

    private data class EditorDraft(
        val name: String,
        val startDate: LocalDate,
        val slots: List<ShiftTypeId>,
        val timings: Map<ShiftTypeId, TypeTiming> = emptyMap(),
        val saveError: UiText? = null,
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

internal fun slotCountError(count: Int): UiText? = when {
    count < PatternLimits.MIN_SLOTS -> UiText.Res(R.string.schedule_error_slots_min)
    count > PatternLimits.MAX_SLOTS -> UiText.Res(R.string.schedule_error_slots_max, listOf(PatternLimits.MAX_SLOTS))
    else -> null
}

private fun moveSlot(slots: List<ShiftTypeId>, fromIndex: Int, toIndex: Int): List<ShiftTypeId> {
    if (fromIndex !in slots.indices || toIndex !in slots.indices || fromIndex == toIndex) return slots
    val next = slots.toMutableList()
    val moved = next.removeAt(fromIndex)
    next.add(toIndex, moved)
    return next
}
