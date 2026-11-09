package com.walhalla.shiftalarm.feature.schedule

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.PatternLimits
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftTypeId
import com.walhalla.shiftalarm.core.model.toOption
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.core.ui.ShiftPane
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.LocalDate

@Composable
fun ScheduleRoute(viewModel: ScheduleViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(
        state = state,
        onApplyTemplate = viewModel::applyTemplate,
        onActivate = viewModel::activate,
        onEditorEvent = viewModel::onEditorEvent,
        onRetry = { viewModel.onEvent(ScheduleEvent.Retry) },
        onDismissError = { viewModel.onEvent(ScheduleEvent.DismissError) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onApplyTemplate: (PatternTemplate) -> Unit,
    onActivate: (String) -> Unit,
    onEditorEvent: (PatternEditorEvent) -> Unit,
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    val shown = rememberReachable(state)
    ShiftPane(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.schedule_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.schedule_templates_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TemplateButton(stringResource(R.string.schedule_template_two_two), PatternTemplate.TWO_TWO, onApplyTemplate)
            TemplateButton(stringResource(R.string.schedule_template_three_three), PatternTemplate.THREE_THREE, onApplyTemplate)
            TemplateButton(stringResource(R.string.schedule_template_one_three), PatternTemplate.ONE_THREE, onApplyTemplate)
            TemplateButton(stringResource(R.string.schedule_template_five_two), PatternTemplate.FIVE_TWO, onApplyTemplate)
            TemplateButton(stringResource(R.string.schedule_template_custom), PatternTemplate.CUSTOM, onApplyTemplate)
        }
        val loadError = shown.loadError
        val commandError = shown.commandError
        when {
            loadError != null -> ErrorBanner(
                message = loadError.resolve(LocalContext.current),
                actionLabel = stringResource(R.string.schedule_retry),
                onAction = onRetry,
            )
            commandError != null -> ErrorBanner(
                message = commandError.resolve(LocalContext.current),
                actionLabel = stringResource(R.string.schedule_dismiss),
                onAction = onDismissError,
            )
        }
        shown.editor?.let { editor ->
            Spacer(Modifier.height(16.dp))
            PatternEditor(editor = editor, onEditorEvent = onEditorEvent)
        }
        Spacer(Modifier.height(20.dp))
        when {
            shown.isLoading -> LoadingBlock(stringResource(R.string.schedule_loading))
            shown.patterns.isEmpty() -> EmptyBlock(
                title = stringResource(R.string.schedule_empty_title),
                body = stringResource(R.string.schedule_empty_body),
            )
            else -> {
                Text(
                    text = stringResource(R.string.schedule_saved_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                shown.patterns.forEach { pattern ->
                    PatternCard(
                        pattern = pattern,
                        active = pattern.id == shown.activePatternId,
                        onActivate = onActivate,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun rememberReachable(state: ScheduleUiState): ScheduleUiState {
    var saved by remember { mutableStateOf<ScheduleUiState?>(null) }
    if (!state.isLoading && state.loadError == null) saved = state
    val previous = saved
    return if (state.loadError != null && previous != null) {
        previous.copy(loadError = state.loadError, commandError = state.commandError)
    } else {
        state
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun TemplateButton(
    label: String,
    template: PatternTemplate,
    onApplyTemplate: (PatternTemplate) -> Unit,
) {
    Button(
        onClick = { onApplyTemplate(template) },
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternEditor(
    editor: PatternEditorUiState,
    onEditorEvent: (PatternEditorEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.schedule_editor_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onEditorEvent(PatternEditorEvent.SetName(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_editor_name)) },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.schedule_editor_start),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onEditorEvent(PatternEditorEvent.SetStartDate(editor.startDate.minusDays(1))) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.schedule_editor_previous_day),
                    )
                }
                Text(
                    text = editor.startDate.formatDayMonth(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onEditorEvent(PatternEditorEvent.SetStartDate(editor.startDate.plusDays(1))) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.schedule_editor_next_day),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.schedule_editor_slots),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.schedule_editor_limit,
                    PatternLimits.MIN_SLOTS,
                    PatternLimits.MAX_SLOTS,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (editor.slots.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.schedule_editor_empty_slots),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            editor.slots.forEachIndexed { index, slotId ->
                val option = editor.availableTypes.firstOrNull { it.id == slotId }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (option != null) {
                        ShiftColorDot(option.colorLight, option.colorDark)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        text = option?.name ?: slotId.value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IconButton(
                        onClick = { onEditorEvent(PatternEditorEvent.MoveSlot(index, index - 1)) },
                        enabled = index > 0,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.schedule_editor_move_up),
                        )
                    }
                    IconButton(
                        onClick = { onEditorEvent(PatternEditorEvent.MoveSlot(index, index + 1)) },
                        enabled = index < editor.slots.lastIndex,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.schedule_editor_move_down),
                        )
                    }
                    IconButton(
                        onClick = { onEditorEvent(PatternEditorEvent.RemoveSlot(index)) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.schedule_editor_remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.schedule_editor_add),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                editor.availableTypes.forEach { type ->
                    OutlinedButton(
                        onClick = { onEditorEvent(PatternEditorEvent.AddSlot(type.id)) },
                        enabled = editor.slots.size < PatternLimits.MAX_SLOTS,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(type.name)
                    }
                }
            }
            editor.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message.resolve(LocalContext.current),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onEditorEvent(PatternEditorEvent.Save) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.schedule_editor_save))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onEditorEvent(PatternEditorEvent.Cancel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.schedule_editor_cancel))
            }
        }
    }
}

@Composable
private fun ShiftColorDot(colorLight: Long, colorDark: Long) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Spacer(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color((if (isDark) colorDark else colorLight).toInt()))
            .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
    )
}

@Composable
private fun PatternCard(
    pattern: PatternSummary,
    active: Boolean,
    onActivate: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(pattern.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = pattern.cycleNames.joinToString(
                    separator = stringResource(R.string.schedule_cycle_separator),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.schedule_start_date, pattern.startDate.formatDayMonth()),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            if (active) {
                Text(
                    text = stringResource(R.string.schedule_active),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            } else {
                OutlinedButton(
                    onClick = { onActivate(pattern.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.schedule_make_active))
                }
            }
        }
    }
}

/** «3 ноября»: day plus the genitive month from resources, never the nominative «ноябрь». */
@Composable
private fun LocalDate.formatDayMonth(): String {
    val genitive = stringArrayResource(R.array.months_genitive)[monthValue - 1]
    return stringResource(R.string.date_d_month, dayOfMonth, genitive)
}

@Composable
private fun LoadingBlock(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                contentDescription = label
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun EmptyBlock(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Schedule light", showBackground = true, widthDp = 360, heightDp = 800, locale = "ru")
@Composable
private fun ScheduleScreenLightPreview() {
    PreviewFrame(darkTheme = false) { ScheduleScreen(sampleScheduleState(), {}, {}, {}) }
}

@Preview(name = "Schedule dark", showBackground = true, widthDp = 360, heightDp = 800, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleScreenDarkPreview() {
    PreviewFrame(darkTheme = true) { ScheduleScreen(sampleScheduleState(), {}, {}, {}) }
}

@Preview(name = "Schedule loading", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun ScheduleScreenLoadingPreview() {
    PreviewFrame(darkTheme = false) { ScheduleScreen(ScheduleUiState(isLoading = true), {}, {}, {}) }
}

@Preview(name = "Schedule loading dark", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleScreenLoadingDarkPreview() {
    PreviewFrame(darkTheme = true) { ScheduleScreen(ScheduleUiState(isLoading = true), {}, {}, {}) }
}

@Preview(name = "Schedule empty", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun ScheduleScreenEmptyPreview() {
    PreviewFrame(darkTheme = false) { ScheduleScreen(ScheduleUiState(isLoading = false), {}, {}, {}) }
}

@Preview(name = "Schedule empty dark", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleScreenEmptyDarkPreview() {
    PreviewFrame(darkTheme = true) { ScheduleScreen(ScheduleUiState(isLoading = false), {}, {}, {}) }
}

@Preview(name = "Schedule error", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun ScheduleScreenErrorPreview() {
    PreviewFrame(darkTheme = false) {
        ScheduleScreen(
            ScheduleUiState(isLoading = false, loadError = UiText.Res(R.string.schedule_error_read)),
            {},
            {},
            {},
        )
    }
}

@Preview(name = "Schedule error dark", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleScreenErrorDarkPreview() {
    PreviewFrame(darkTheme = true) {
        ScheduleScreen(
            ScheduleUiState(isLoading = false, loadError = UiText.Res(R.string.schedule_error_read)),
            {},
            {},
            {},
        )
    }
}

@Preview(name = "Schedule editor", showBackground = true, widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun ScheduleEditorPreview() {
    PreviewFrame(darkTheme = false) {
        ScheduleScreen(
            state = ScheduleUiState(
                isLoading = false,
                editor = PatternEditorUiState(
                    name = "Свой график",
                    startDate = LocalDate.of(2026, 11, 3),
                    slots = listOf(ShiftTypeId(BuiltinShifts.DAY_ID), ShiftTypeId(BuiltinShifts.OFF_ID)),
                    availableTypes = listOf(BuiltinShifts.day, BuiltinShifts.night, BuiltinShifts.off).map { it.toOption() },
                ),
            ),
            onApplyTemplate = {},
            onActivate = {},
            onEditorEvent = {},
        )
    }
}

@Composable
private fun PreviewFrame(darkTheme: Boolean, content: @Composable () -> Unit) {
    ShiftAlarmTheme(darkTheme = darkTheme, useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun sampleScheduleState(): ScheduleUiState = ScheduleUiState(
    isLoading = false,
    activePatternId = "pattern-2-2",
    patterns = listOf(
        PatternSummary(
            id = "pattern-2-2",
            name = "2/2",
            template = PatternTemplate.TWO_TWO,
            cycleNames = listOf("День", "Ночь", "Отсыпной", "Выходной"),
            startDate = LocalDate.of(2026, 11, 3),
        ),
        PatternSummary(
            id = "pattern-5-2",
            name = "5/2",
            template = PatternTemplate.FIVE_TWO,
            cycleNames = listOf("День", "День", "День", "День", "День", "Выходной", "Выходной"),
            startDate = LocalDate.of(2026, 12, 10),
        ),
    ),
)
