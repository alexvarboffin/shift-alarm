package com.walhalla.shiftalarm.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.ShiftCalculator
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CalendarViewModel @Inject constructor(
    shiftRepository: ShiftRepository,
    alarmRepository: AlarmRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        shiftRepository.observeActivePattern(),
        shiftRepository.observeOverrides(),
        alarmRepository.observeExceptions(),
    ) { visibleMonth, selected, pattern, overrides, exceptions ->
        val days = buildDays(visibleMonth, pattern, overrides, exceptions)
        CalendarUiState(
            isLoading = false,
            month = visibleMonth,
            days = days,
            selected = days.firstOrNull { it.date == selected },
            hasActivePattern = pattern != null && pattern.cycle.isNotEmpty(),
        )
    }.catch { error ->
        emit(
            CalendarUiState(
                isLoading = false,
                errorMessage = error.message ?: "Не удалось собрать месяц",
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun showPreviousMonth() {
        month.value = month.value.minusMonths(1)
        selectedDate.value = null
    }

    fun showNextMonth() {
        month.value = month.value.plusMonths(1)
        selectedDate.value = null
    }

    fun select(date: LocalDate) {
        selectedDate.value = date
    }

    private fun buildDays(
        month: YearMonth,
        pattern: ShiftPattern?,
        overrides: List<DayOverride>,
        exceptions: List<AlarmException>,
    ): List<CalendarDayUi> {
        val overrideMap = overrides.associateBy { it.date }
        val exceptionMap = exceptions.associateBy { it.date }
        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            if (pattern == null || pattern.cycle.isEmpty()) {
                CalendarDayUi(date, null, null, null, false, null)
            } else {
                val resolved = ShiftCalculator.shiftFor(date, pattern, overrideMap)
                val exception = exceptionMap[date]
                val alarm = when {
                    exception?.enabled == false -> null
                    exception?.time != null -> exception.time
                    else -> resolved.shift.alarmTime
                }
                CalendarDayUi(
                    date = date,
                    shiftName = resolved.shift.name,
                    shiftKind = resolved.shift.kind,
                    colorArgb = resolved.shift.colorArgb,
                    hasOverride = resolved.source == ShiftSource.OVERRIDE,
                    alarmLabel = alarm?.let { "%02d:%02d".format(it.hour, it.minute) },
                )
            }
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
