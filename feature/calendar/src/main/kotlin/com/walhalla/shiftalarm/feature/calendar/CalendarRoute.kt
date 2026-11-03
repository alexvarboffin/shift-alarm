package com.walhalla.shiftalarm.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.ui.ShiftPane

@Composable
fun CalendarRoute(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        state = state,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSelect = viewModel::select,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelect: (java.time.LocalDate) -> Unit,
) {
    ShiftPane(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
            }
            Text(
                text = monthTitle(state.month.monthValue, state.month.year),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.errorMessage != null -> Text(
                state.errorMessage.resolve(LocalContext.current),
                color = MaterialTheme.colorScheme.error,
            )
            else -> {
                if (!state.hasActivePattern) {
                    Text("Сначала выберите график. Дни считает ShiftCalculator.")
                    Spacer(Modifier.height(8.dp))
                }
                WeekHeader()
                MonthGrid(state.days, state.selected?.date, onSelect)
                state.selected?.let { day ->
                    Spacer(Modifier.height(12.dp))
                    Text(day.date.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(day.shiftName ?: "Нет смены")
                    Text(day.alarmLabel?.let { "Будильник $it" } ?: "Без будильника")
                    if (day.hasOverride) Text("Исключение на эту дату")
                }
            }
        }
    }
}

private fun monthTitle(month: Int, year: Int): String {
    val name = listOf(
        "Январь",
        "Февраль",
        "Март",
        "Апрель",
        "Май",
        "Июнь",
        "Июль",
        "Август",
        "Сентябрь",
        "Октябрь",
        "Ноябрь",
        "Декабрь",
    ).getOrElse(month - 1) { month.toString() }
    return "$name $year"
}

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<CalendarDayUi>,
    selected: java.time.LocalDate?,
    onSelect: (java.time.LocalDate) -> Unit,
) {
    if (days.isEmpty()) return
    val leading = days.first().date.dayOfWeek.value - 1
    val cells = List(leading) { null } + days
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .padding(2.dp)
                        .then(
                            if (day != null) Modifier.clickable { onSelect(day.date) } else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                color = if (day.date == selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (day.colorArgb != null) {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(day.colorArgb)),
                                )
                            }
                            Text(
                                text = shortLabel(day.shiftKind, day.shiftName),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            repeat(7 - week.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
