package com.walhalla.shiftalarm.feature.calendar

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.ShiftCalculator
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.model.toOption
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * [onOpenSchedule] is navigation only: the empty state offers it when no cycle is active.
 */
@Composable
fun CalendarRoute(
    onOpenSchedule: (() -> Unit)? = null,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        state = state,
        onSelect = viewModel::select,
        onEvent = viewModel::onEvent,
        onOpenSchedule = onOpenSchedule,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onSelect: (LocalDate) -> Unit,
    onEvent: (CalendarEvent) -> Unit,
    onOpenSchedule: (() -> Unit)? = null,
) {
    val shown = rememberReachable(state)
    val loadError = shown.loadError
    val commandError = shown.commandError
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                MonthSwitcher(
                    month = shown.month,
                    onPreviousMonth = { onEvent(CalendarEvent.ShowMonth(shown.month.minusMonths(1))) },
                    onNextMonth = { onEvent(CalendarEvent.ShowMonth(shown.month.plusMonths(1))) },
                )
                when {
                    loadError != null -> ErrorBanner(
                        message = loadError.resolve(LocalContext.current),
                        actionLabel = stringResource(R.string.calendar_retry),
                        onAction = { onEvent(CalendarEvent.Retry) },
                    )
                    commandError != null -> ErrorBanner(
                        message = commandError.resolve(LocalContext.current),
                        actionLabel = stringResource(R.string.calendar_dismiss),
                        onAction = { onEvent(CalendarEvent.DismissError) },
                    )
                }
                when {
                    shown.isLoading -> LoadingBlock(stringResource(R.string.calendar_loading))
                    !shown.hasActivePattern -> EmptyBlock(
                        title = stringResource(R.string.calendar_empty_no_pattern_title),
                        body = stringResource(R.string.calendar_no_pattern),
                        actionLabel = stringResource(R.string.calendar_open_schedule),
                        onAction = onOpenSchedule,
                    )
                    shown.days.isEmpty() -> EmptyBlock(
                        title = stringResource(R.string.calendar_empty),
                        body = null,
                        actionLabel = null,
                        onAction = null,
                    )
                }
            }
            if (!shown.isLoading && shown.hasActivePattern && shown.days.isNotEmpty()) {
                MonthBody(
                    days = shown.days,
                    selected = shown.selected?.date,
                    onSelect = onSelect,
                )
                shown.selected?.let { day ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        SelectedDayCard(day)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    val day = shown.selectedDay
    if (day != null) {
        ModalBottomSheet(onDismissRequest = { onEvent(CalendarEvent.ClearSelection) }) {
            DayEditor(day = day, onEvent = onEvent)
        }
    }
}

@Composable
private fun rememberReachable(state: CalendarUiState): CalendarUiState {
    var saved by remember { mutableStateOf<CalendarUiState?>(null) }
    if (!state.isLoading && state.loadError == null) saved = state
    val previous = saved
    return if (state.loadError != null && previous != null) {
        previous.copy(loadError = state.loadError, commandError = state.commandError)
    } else {
        state
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(
            text = monthTitle(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = onNextMonth, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }
}

@Composable
private fun monthTitle(month: YearMonth): String {
    val names = stringArrayResource(R.array.calendar_months)
    val name = names.getOrElse(month.monthValue - 1) { month.monthValue.toString() }
    return stringResource(R.string.calendar_month_year, name, month.year)
}

@Composable
private fun MonthBody(
    days: List<CalendarDayUi>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = maxOf(48.dp, maxWidth / 7)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(cellWidth * 7)) {
                WeekHeader(cellWidth)
                MonthGrid(days, selected, onSelect, cellWidth)
            }
        }
    }
}

@Composable
private fun WeekHeader(cellWidth: Dp) {
    Row(Modifier.fillMaxWidth()) {
        stringArrayResource(R.array.calendar_weekdays).forEach { label ->
            Text(
                text = label,
                modifier = Modifier.width(cellWidth).padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<CalendarDayUi>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    cellWidth: Dp,
) {
    if (days.isEmpty()) return
    val today = LocalDate.now()
    val leading = (days.first().date.dayOfWeek.value - 1).coerceAtLeast(0)
    val cells: List<CalendarDayUi?> = List(leading) { null } + days
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            week.forEach { day ->
                if (day == null) {
                    Spacer(Modifier.width(cellWidth).heightIn(min = 64.dp))
                } else {
                    DayCell(day, day.date == selected, day.date == today, cellWidth, onSelect)
                }
            }
            repeat(7 - week.size) {
                Spacer(Modifier.width(cellWidth).heightIn(min = 64.dp))
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDayUi,
    isSelected: Boolean,
    today: Boolean,
    cellWidth: Dp,
    onSelect: (LocalDate) -> Unit,
) {
    val description = dayDescription(day, today)
    Column(
        modifier = Modifier
            .width(cellWidth)
            .heightIn(min = 64.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
            )
            .then(
                if (today) Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) else Modifier,
            )
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.calendar_select_day),
                onClick = { onSelect(day.date) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        shiftColor(day.colorLight, day.colorDark)?.let { ShiftColorDot(it) }
        val short = shortShiftLabel(day)
        if (short.isNotEmpty()) {
            Text(short, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun dayDescription(day: CalendarDayUi, today: Boolean): String {
    val shift = day.shiftName ?: stringResource(R.string.calendar_no_shift)
    val parts = mutableListOf(stringResource(R.string.calendar_day_cd, day.date.dayOfMonth, shift))
    if (today) parts += stringResource(R.string.calendar_today)
    parts += day.alarmLabel?.let { stringResource(R.string.calendar_alarm, it) }
        ?: stringResource(R.string.calendar_no_alarm)
    if (day.hasOverride) parts += stringResource(R.string.calendar_override)
    return parts.joinToString(". ")
}

@Composable
private fun shortShiftLabel(day: CalendarDayUi): String = when {
    day.shiftName == BuiltinShifts.duty.name -> stringResource(R.string.calendar_short_duty)
    day.shiftKind == ShiftKind.DAY -> stringResource(R.string.calendar_short_day)
    day.shiftKind == ShiftKind.NIGHT -> stringResource(R.string.calendar_short_night)
    day.shiftKind == ShiftKind.AFTER_NIGHT -> stringResource(R.string.calendar_short_after)
    day.shiftKind == ShiftKind.OFF -> stringResource(R.string.calendar_short_off)
    day.shiftName != null -> day.shiftName.take(1)
    else -> ""
}

@Composable
private fun SelectedDayCard(day: CalendarDayUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.date.formatDayMonth(), style = MaterialTheme.typography.titleMedium)
            Text(day.shiftName ?: stringResource(R.string.calendar_no_shift), style = MaterialTheme.typography.bodyLarge)
            Text(
                day.alarmLabel?.let { stringResource(R.string.calendar_alarm, it) }
                    ?: stringResource(R.string.calendar_no_alarm),
            )
            if (day.hasOverride) {
                Text(
                    stringResource(R.string.calendar_override),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayEditor(
    day: SelectedDayDetail,
    onEvent: (CalendarEvent) -> Unit,
) {
    val time = day.displayedTime()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = day.date.formatDayMonth(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.calendar_shift_type), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (day.availableTypes.isEmpty()) {
            Text(stringResource(R.string.calendar_no_types))
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                day.availableTypes.forEach { type ->
                    val chosen = type.id == day.shiftTypeId
                    if (chosen) {
                        Button(
                            onClick = { onEvent(CalendarEvent.SetOverride(day.date, type.id)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(type.name) }
                    } else {
                        OutlinedButton(
                            onClick = { onEvent(CalendarEvent.SetOverride(day.date, type.id)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(type.name) }
                    }
                }
            }
        }
        if (day.hasOverride) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onEvent(CalendarEvent.ClearOverride(day.date)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.calendar_clear_override))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = day.alarmEnabled,
                    role = Role.Switch,
                    onValueChange = { enabled ->
                        onEvent(
                            CalendarEvent.SetAlarmException(
                                date = day.date,
                                enabled = enabled,
                                customTime = if (enabled) day.customAlarmTime else null,
                            ),
                        )
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.calendar_alarm_switch),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(checked = day.alarmEnabled, onCheckedChange = null)
        }
        if (day.alarmEnabled) {
            Text(stringResource(R.string.calendar_alarm_time), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeStep(
                    minus = stringResource(R.string.calendar_hour_minus),
                    plus = stringResource(R.string.calendar_hour_plus),
                    onMinus = {
                        onEvent(CalendarEvent.SetAlarmException(day.date, true, time.minusHours(1)))
                    },
                    onPlus = {
                        onEvent(CalendarEvent.SetAlarmException(day.date, true, time.plusHours(1)))
                    },
                )
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "%02d:%02d".format(time.hour, time.minute),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (time.toLocalDate() != day.date) {
                        Text(
                            text = time.toLocalDate().formatDayMonth(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                TimeStep(
                    minus = stringResource(R.string.calendar_minute_minus),
                    plus = stringResource(R.string.calendar_minute_plus),
                    onMinus = {
                        onEvent(CalendarEvent.SetAlarmException(day.date, true, time.minusMinutes(5)))
                    },
                    onPlus = {
                        onEvent(CalendarEvent.SetAlarmException(day.date, true, time.plusMinutes(5)))
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onEvent(CalendarEvent.ClearAlarmException(day.date)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.calendar_clear_alarm))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimeStep(
    minus: String,
    plus: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column {
        IconButton(onClick = onPlus, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = plus)
        }
        IconButton(onClick = onMinus, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = minus)
        }
    }
}

/** Absolute alarm moment shown in the editor: the exception, or shift start minus lead. */
private fun SelectedDayDetail.displayedTime(): LocalDateTime {
    customAlarmTime?.let { return it }
    val start = startTime ?: return date.atTime(DEFAULT_ALARM_HOUR, 0)
    return date.atTime(start).minusMinutes((alarmLeadMinutes ?: 0).toLong())
}

private const val DEFAULT_ALARM_HOUR = 7

/** Picks the light or dark shift color by the current Material surface, not the system flag. */
@Composable
private fun shiftColor(light: Long?, dark: Long?): Color? {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val argb = if (isDark) dark ?: light else light ?: dark
    return argb?.let { Color(it.toInt()) }
}

@Composable
private fun ShiftColorDot(color: Color) {
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
    )
}

/** «3 ноября»: day plus the genitive month from resources, never the nominative «ноябрь». */
@Composable
private fun LocalDate.formatDayMonth(): String {
    val genitive = stringArrayResource(R.array.months_genitive)[monthValue - 1]
    return stringResource(R.string.date_d_month, dayOfMonth, genitive)
}

@Composable
private fun ErrorBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock(label: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                contentDescription = label
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(label, modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyBlock(
    title: String,
    body: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(name = "Calendar light", showBackground = true, widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun CalendarLightPreview() {
    PreviewFrame(false) { CalendarScreen(sampleCalendarState(), {}, {}) }
}

@Preview(name = "Calendar dark", showBackground = true, widthDp = 360, heightDp = 800, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarDarkPreview() {
    PreviewFrame(true) { CalendarScreen(sampleCalendarState(), {}, {}) }
}

@Preview(name = "Calendar loading", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru")
@Composable
private fun CalendarLoadingPreview() {
    PreviewFrame(false) { CalendarScreen(CalendarUiState(isLoading = true, month = YearMonth.of(2026, 11)), {}, {}) }
}

@Preview(name = "Calendar loading dark", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarLoadingDarkPreview() {
    PreviewFrame(true) { CalendarScreen(CalendarUiState(isLoading = true, month = YearMonth.of(2026, 11)), {}, {}) }
}

@Preview(name = "Calendar empty", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru")
@Composable
private fun CalendarEmptyPreview() {
    PreviewFrame(false) {
        CalendarScreen(CalendarUiState(isLoading = false, month = YearMonth.of(2026, 11), hasActivePattern = false), {}, {}, onOpenSchedule = {})
    }
}

@Preview(name = "Calendar empty dark", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarEmptyDarkPreview() {
    PreviewFrame(true) {
        CalendarScreen(CalendarUiState(isLoading = false, month = YearMonth.of(2026, 11), hasActivePattern = false), {}, {}, onOpenSchedule = {})
    }
}

@Preview(name = "Calendar error", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun CalendarErrorPreview() {
    PreviewFrame(false) {
        CalendarScreen(
            sampleCalendarState().copy(loadError = UiText.Res(R.string.calendar_error_load)),
            {},
            {},
        )
    }
}

@Preview(name = "Calendar error dark", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarErrorDarkPreview() {
    PreviewFrame(true) {
        CalendarScreen(
            sampleCalendarState().copy(loadError = UiText.Res(R.string.calendar_error_load)),
            {},
            {},
        )
    }
}

@Composable
private fun PreviewFrame(darkTheme: Boolean, content: @Composable () -> Unit) {
    ShiftAlarmTheme(darkTheme = darkTheme, useDynamicColor = false) {
        Surface(Modifier.fillMaxSize(), content = content)
    }
}

private fun sampleCalendarState(): CalendarUiState {
    val month = YearMonth.of(2026, 11)
    val pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 11, 1))
    val days = (1..month.lengthOfMonth()).map { dayNumber ->
        val date = month.atDay(dayNumber)
        val resolved = ShiftCalculator.shiftFor(date, pattern)
        CalendarDayUi(
            date = date,
            shiftName = resolved.shift.name,
            shiftKind = resolved.shift.kind,
            colorLight = resolved.shift.colorLight,
            colorDark = resolved.shift.colorDark,
            hasOverride = date.dayOfMonth == 12,
            alarmLabel = resolved.shift.startTime
                ?.minusMinutes(resolved.shift.alarmLeadMinutes.toLong())
                ?.let { "%02d:%02d".format(it.hour, it.minute) },
        )
    }
    val selected = days[9]
    return CalendarUiState(
        isLoading = false,
        month = month,
        days = days,
        selected = selected,
        hasActivePattern = true,
        selectedDay = SelectedDayDetail(
            date = selected.date,
            shiftTypeId = ShiftTypeId(BuiltinShifts.DAY_ID),
            shiftName = selected.shiftName,
            shiftKind = selected.shiftKind,
            colorLight = selected.colorLight,
            colorDark = selected.colorDark,
            hasOverride = false,
            alarmEnabled = true,
            startTime = BuiltinShifts.day.startTime,
            alarmLeadMinutes = BuiltinShifts.day.alarmLeadMinutes,
            customAlarmTime = null,
            alarmLabel = selected.alarmLabel,
            availableTypes = listOf(BuiltinShifts.day, BuiltinShifts.night, BuiltinShifts.off).map { it.toOption() },
        ),
    )
}
