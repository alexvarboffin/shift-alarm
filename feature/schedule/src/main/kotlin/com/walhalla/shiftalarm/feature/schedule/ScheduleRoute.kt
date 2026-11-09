package com.walhalla.shiftalarm.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.ui.ShiftPane
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ScheduleRoute(viewModel: ScheduleViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(
        state = state,
        onApplyTemplate = viewModel::applyTemplate,
        onActivate = viewModel::activate,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onApplyTemplate: (PatternTemplate) -> Unit,
    onActivate: (String) -> Unit,
) {
    ShiftPane(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Цикл начнётся с сегодняшнего дня. Потом его можно сменить на календаре точечными исключениями.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TemplateButton("2/2", PatternTemplate.TWO_TWO, onApplyTemplate)
            TemplateButton("3/3", PatternTemplate.THREE_THREE, onApplyTemplate)
            TemplateButton("Сутки-трое", PatternTemplate.ONE_THREE, onApplyTemplate)
            TemplateButton("5/2", PatternTemplate.FIVE_TWO, onApplyTemplate)
        }
        Spacer(Modifier.height(20.dp))
        when {
            state.isLoading -> CircularProgressIndicator()
            state.errorMessage != null -> Text(
                text = state.errorMessage.resolve(LocalContext.current),
                color = MaterialTheme.colorScheme.error,
            )
            state.patterns.isEmpty() -> Text(
                text = "Нет сохранённого графика. Выберите шаблон выше.",
                style = MaterialTheme.typography.titleMedium,
            )
            else -> state.patterns.forEach { pattern ->
                PatternCard(
                    pattern = pattern,
                    active = pattern.id == state.activePatternId,
                    onActivate = onActivate,
                )
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
    Button(onClick = { onApplyTemplate(template) }) {
        Text(label)
    }
}

@Composable
private fun PatternCard(
    pattern: PatternSummary,
    active: Boolean,
    onActivate: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(pattern.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = pattern.cycleNames.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Старт ${pattern.startDate}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (active) {
            Text("Активный", color = MaterialTheme.colorScheme.tertiary)
        } else {
            OutlinedButton(onClick = { onActivate(pattern.id) }) {
                Text("Сделать активным")
            }
        }
    }
}
