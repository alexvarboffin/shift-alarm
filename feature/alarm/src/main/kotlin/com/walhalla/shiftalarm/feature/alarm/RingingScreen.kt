package com.walhalla.shiftalarm.feature.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RingingScreen(
    state: RingingUiState,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    val clock = Instant.ofEpochMilli(state.triggerAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Смена", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(state.shiftName.ifBlank { "Будильник" }, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(clock, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStop) {
            Text("Стоп")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSnooze) {
            Text("Отложить на ${state.snoozeMinutes} мин")
        }
    }
}
