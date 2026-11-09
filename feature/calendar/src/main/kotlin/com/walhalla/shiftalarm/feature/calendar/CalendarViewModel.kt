package com.walhalla.shiftalarm.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.model.AlarmException as StoredAlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.HomeWidgetRefresher
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.ShiftAlarms
import com.walhalla.shiftalarm.core.model.ShiftCalculator
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftSource
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.ShiftTypeCatalog
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.model.ShiftTypeOption
import com.walhalla.shiftalarm.core.model.keepAlive
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val alarmRepository: AlarmRepository,
    private val widgetRefresher: HomeWidgetRefresher,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow(restoreMonth(savedState, clock))
    private val selectedDate = MutableStateFlow(restoreDay(savedState))
    private val commandError = MutableStateFlow<UiText?>(null)
    private val readError = MutableStateFlow<UiText?>(null)
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<CalendarUiState> = combine(
        combine(month, selectedDate, commandError, readError) { visibleMonth, selected, command, read ->
            Cursor(visibleMonth, selected, command ?: read)
        },
        reload.flatMapLatest { shiftRepository.observeActivePattern().keepAlive(null) { readError.value = loadError() } },
        reload.flatMapLatest { shiftRepository.observeOverrides().keepAlive(emptyList()) { readError.value = loadError() } },
        reload.flatMapLatest { shiftRepository.observeShiftTypes().keepAlive(emptyList()) { readError.value = loadError() } },
        reload.flatMapLatest { alarmRepository.observeExceptions().keepAlive(emptyList()) { readError.value = loadError() } },
    ) { cursor, pattern, overrides, storedTypes, exceptions ->
        val options = ShiftTypeCatalog.options(storedTypes)
        val days = buildDays(cursor.month, pattern, overrides, exceptions)
        val selectedCell = days.firstOrNull { it.date == cursor.selected }
        CalendarUiState(
            isLoading = false,
            month = cursor.month,
            days = days,
            selected = selectedCell,
            selectedDay = cursor.selected?.let { date ->
                detail(date, pattern, overrides, exceptions, options)
            },
            hasActivePattern = pattern != null && pattern.cycle.isNotEmpty(),
            errorMessage = cursor.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(month = YearMonth.from(LocalDate.now(clock))),
    )

    private fun loadError(): UiText = UiText.Res(R.string.calendar_error_load)

    private data class Cursor(
        val month: YearMonth,
        val selected: LocalDate?,
        val error: UiText?,
    )

    fun showMonth(month: YearMonth) {
        this.month.value = month
        savedState[KEY_MONTH] = month.toString()
        selectedDate.value = null
        savedState[KEY_DAY] = null
    }

    fun showPreviousMonth() {
        showMonth(month.value.minusMonths(1))
    }

    fun showNextMonth() {
        showMonth(month.value.plusMonths(1))
    }

    fun select(date: LocalDate) {
        selectedDate.value = date
        savedState[KEY_DAY] = date.toString()
    }

    /** One-off replacement of the pattern slot. Same name as the feature ViewModel. */
    fun setOverride(date: LocalDate, shift: ShiftType) {
        viewModelScope.launch {
            shiftRepository.upsertOverride(DayOverride(date, shift))
            widgetRefresher.refresh()
            commandError.value = null
        }
    }

    fun setOverride(date: LocalDate, shiftTypeId: ShiftTypeId?) {
        if (shiftTypeId == null) {
            clearOverride(date)
            return
        }
        viewModelScope.launch {
            val type = ShiftTypeCatalog.find(shiftRepository.observeShiftTypes().first(), shiftTypeId)
            if (type == null) {
                commandError.value = UiText.Res(R.string.calendar_error_unknown_type)
            } else {
                shiftRepository.upsertOverride(DayOverride(date, type))
                widgetRefresher.refresh()
                commandError.value = null
            }
        }
    }

    fun clearOverride(date: LocalDate) {
        viewModelScope.launch {
            shiftRepository.deleteOverride(date)
            widgetRefresher.refresh()
            commandError.value = null
        }
    }

    fun setAlarmException(exception: StoredAlarmException) {
        viewModelScope.launch {
            alarmRepository.upsertException(exception)
            commandError.value = null
        }
    }

    fun clearAlarmException(date: LocalDate) {
        viewModelScope.launch {
            alarmRepository.deleteException(date)
            commandError.value = null
        }
    }

    /** "Retry": clears both errors and subscribes to the repositories again. */
    fun retry() {
        commandError.value = null
        readError.value = null
        reload.value += 1
    }

    fun onEvent(event: CalendarEvent) {
        when (event) {
            is CalendarEvent.SetOverride -> setOverride(event.date, event.shiftTypeId)
            is CalendarEvent.ClearOverride -> clearOverride(event.date)
            is CalendarEvent.SetAlarmException -> setAlarmException(
                StoredAlarmException(
                    date = event.date,
                    enabled = event.enabled,
                    customTime = if (event.enabled) event.customTime else null,
                ),
            )
            is CalendarEvent.ClearAlarmException -> clearAlarmException(event.date)
            is CalendarEvent.ShowMonth -> showMonth(event.month)
            CalendarEvent.Retry -> retry()
        }
    }

    private fun detail(
        date: LocalDate,
        pattern: ShiftPattern?,
        overrides: List<DayOverride>,
        exceptions: List<StoredAlarmException>,
        options: List<ShiftTypeOption>,
    ): SelectedDayDetail {
        val overrideMap = overrides.associateBy { it.date }
        val exception = exceptions.firstOrNull { it.date == date }
        val resolved = if (pattern == null || pattern.cycle.isEmpty()) {
            null
        } else {
            ShiftCalculator.shiftFor(date, pattern, overrideMap)
        }
        val alarm = alarmInstant(date, resolved?.shift, exception)
        return SelectedDayDetail(
            date = date,
            shiftTypeId = resolved?.shift?.id?.let(::ShiftTypeId),
            shiftName = resolved?.shift?.name,
            shiftKind = resolved?.shift?.kind,
            colorLight = resolved?.shift?.colorLight,
            colorDark = resolved?.shift?.colorDark,
            hasOverride = resolved?.source == ShiftSource.OVERRIDE,
            alarmEnabled = alarm != null,
            startTime = resolved?.shift?.startTime,
            alarmLeadMinutes = resolved?.shift?.alarmLeadMinutes,
            customAlarmTime = exception?.takeIf { it.enabled }?.customTime,
            alarmLabel = alarm?.toLocalTime()?.let { "%02d:%02d".format(it.hour, it.minute) },
            availableTypes = options,
        )
    }

    private fun buildDays(
        month: YearMonth,
        pattern: ShiftPattern?,
        overrides: List<DayOverride>,
        exceptions: List<StoredAlarmException>,
    ): List<CalendarDayUi> {
        val overrideMap = overrides.associateBy { it.date }
        val exceptionMap = exceptions.associateBy { it.date }
        if (pattern == null || pattern.cycle.isEmpty()) return emptyList()
        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val resolved = ShiftCalculator.shiftFor(date, pattern, overrideMap)
            val alarm = alarmInstant(date, resolved.shift, exceptionMap[date])
            CalendarDayUi(
                date = date,
                shiftName = resolved.shift.name,
                shiftKind = resolved.shift.kind,
                colorLight = resolved.shift.colorLight,
                colorDark = resolved.shift.colorDark,
                hasOverride = resolved.source == ShiftSource.OVERRIDE,
                alarmLabel = alarm?.toLocalTime()?.let { "%02d:%02d".format(it.hour, it.minute) },
            )
        }
    }

    private fun alarmInstant(
        date: LocalDate,
        shift: ShiftType?,
        exception: StoredAlarmException?,
    ): ZonedDateTime? {
        val zone = clock.zone
        val custom = exception?.customTime
        return when {
            exception?.enabled == false -> null
            custom != null -> ShiftAlarms.exceptionInstant(custom, zone)
            shift == null -> null
            else -> ShiftAlarms.instant(date, shift, zone)
        }
    }
}

internal const val KEY_MONTH: String = "calendar.month"
internal const val KEY_DAY: String = "calendar.day"

internal fun restoreMonth(savedState: SavedStateHandle, clock: Clock): YearMonth =
    savedState.get<String>(KEY_MONTH)?.let { raw -> runCatching { YearMonth.parse(raw) }.getOrNull() }
        ?: YearMonth.from(LocalDate.now(clock))

internal fun restoreDay(savedState: SavedStateHandle): LocalDate? =
    savedState.get<String>(KEY_DAY)?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }

internal fun shortLabel(kind: ShiftKind?, name: String?): String = when {
    name == BuiltinShifts.duty.name -> "С"
    kind == ShiftKind.DAY -> "Д"
    kind == ShiftKind.NIGHT -> "Н"
    kind == ShiftKind.AFTER_NIGHT -> "От"
    kind == ShiftKind.OFF -> "В"
    name != null -> name.take(1)
    else -> ""
}
