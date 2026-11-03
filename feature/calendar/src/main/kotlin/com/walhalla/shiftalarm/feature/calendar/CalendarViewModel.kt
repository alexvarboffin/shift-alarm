package com.walhalla.shiftalarm.feature.calendar

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
import com.walhalla.shiftalarm.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val alarmRepository: AlarmRepository,
    private val widgetRefresher: HomeWidgetRefresher,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val commandError = MutableStateFlow<UiText?>(null)

    val uiState: StateFlow<CalendarUiState> = combine(
        combine(month, selectedDate, commandError) { visibleMonth, selected, error ->
            Triple(visibleMonth, selected, error)
        },
        shiftRepository.observeActivePattern(),
        shiftRepository.observeOverrides(),
        shiftRepository.observeShiftTypes(),
        alarmRepository.observeExceptions(),
    ) { cursor, pattern, overrides, storedTypes, exceptions ->
        val (visibleMonth, selected, error) = cursor
        val options = ShiftTypeCatalog.options(storedTypes)
        val days = buildDays(visibleMonth, pattern, overrides, exceptions)
        val selectedCell = days.firstOrNull { it.date == selected }
        CalendarUiState(
            isLoading = false,
            month = visibleMonth,
            days = days,
            selected = selectedCell,
            selectedDay = selected?.let { date ->
                detail(date, pattern, overrides, exceptions, options)
            },
            hasActivePattern = pattern != null && pattern.cycle.isNotEmpty(),
            errorMessage = error,
        )
    }.catch { error ->
        emit(
            CalendarUiState(
                isLoading = false,
                errorMessage = UiText.Raw(error.message ?: "Не удалось собрать месяц"),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun showMonth(month: YearMonth) {
        this.month.value = month
        selectedDate.value = null
    }

    fun showPreviousMonth() {
        showMonth(month.value.minusMonths(1))
    }

    fun showNextMonth() {
        showMonth(month.value.plusMonths(1))
    }

    fun select(date: LocalDate) {
        selectedDate.value = date
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
                commandError.value = UiText.Raw("Неизвестный тип смены.")
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
            colorArgb = resolved?.shift?.colorArgb,
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
        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            if (pattern == null || pattern.cycle.isEmpty()) {
                CalendarDayUi(date, null, null, null, false, null)
            } else {
                val resolved = ShiftCalculator.shiftFor(date, pattern, overrideMap)
                val alarm = alarmInstant(date, resolved.shift, exceptionMap[date])
                CalendarDayUi(
                    date = date,
                    shiftName = resolved.shift.name,
                    shiftKind = resolved.shift.kind,
                    colorArgb = resolved.shift.colorArgb,
                    hasOverride = resolved.source == ShiftSource.OVERRIDE,
                    alarmLabel = alarm?.toLocalTime()?.let { "%02d:%02d".format(it.hour, it.minute) },
                )
            }
        }
    }

    private fun alarmInstant(
        date: LocalDate,
        shift: ShiftType?,
        exception: StoredAlarmException?,
    ): ZonedDateTime? {
        val zone = ZoneId.systemDefault()
        val custom = exception?.customTime
        return when {
            exception?.enabled == false -> null
            custom != null -> ShiftAlarms.exceptionInstant(custom, zone)
            shift == null -> null
            else -> ShiftAlarms.instant(date, shift, zone)
        }
    }
}

internal fun shortLabel(kind: ShiftKind?, name: String?): String = when {
    name == BuiltinShifts.duty.name -> "С"
    kind == ShiftKind.DAY -> "Д"
    kind == ShiftKind.NIGHT -> "Н"
    kind == ShiftKind.AFTER_NIGHT -> "От"
    kind == ShiftKind.OFF -> "В"
    name != null -> name.take(1)
    else -> ""
}
