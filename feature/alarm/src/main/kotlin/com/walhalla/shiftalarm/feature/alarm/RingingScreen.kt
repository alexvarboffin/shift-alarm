package com.walhalla.shiftalarm.feature.alarm

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.core.ui.UiText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun RingingScreen(
    state: RingingUiState,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when (state) {
            RingingUiState.Loading -> StatusColumn(
                message = stringResource(R.string.ringing_loading),
                loading = true,
                onDismiss = null,
            )
            is RingingUiState.Error -> StatusColumn(
                message = state.errorMessage.resolve(LocalContext.current),
                loading = false,
                onDismiss = onStop,
            )
            is RingingUiState.Ringing -> RingingContent(state, onStop, onSnooze)
        }
    }
}

@Composable
private fun StatusColumn(
    message: String,
    loading: Boolean,
    onDismiss: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (onDismiss != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.ringing_dismiss))
            }
        }
    }
}

@Composable
private fun RingingContent(
    state: RingingUiState.Ringing,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    val shiftName = state.shiftName.ifBlank { stringResource(R.string.ringing_fallback_name) }
    val clock = clockText(state.triggerAtEpochMillis)
    val clockDescription = stringResource(R.string.ringing_time_cd, clock)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1.2f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ClockBlock(shiftName, clock, clockDescription, compact = true)
                }
                Spacer(Modifier.width(24.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlarmButtons(state.snoozeMinutes, onStop, onSnooze, minHeight = 64.dp)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.ringing_icon_cd),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    ClockBlock(shiftName, clock, clockDescription, compact = false)
                }
                Spacer(Modifier.height(24.dp))
                AlarmButtons(state.snoozeMinutes, onStop, onSnooze, minHeight = 72.dp)
            }
        }
    }
}

@Composable
private fun ClockBlock(
    shiftName: String,
    clock: String,
    clockDescription: String,
    compact: Boolean,
) {
    Text(
        text = clock,
        style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = clockDescription },
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.ringing_shift_label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = shiftName,
        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}

@Composable
private fun AlarmButtons(
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    minHeight: androidx.compose.ui.unit.Dp,
) {
    Button(
        onClick = onStop,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
    ) {
        Text(
            text = stringResource(R.string.ringing_stop),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    if (minHeight > 64.dp) {
        Spacer(Modifier.height(12.dp))
    }
    OutlinedButton(
        onClick = onSnooze,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
    ) {
        Text(
            text = stringResource(R.string.ringing_snooze, snoozeMinutes),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun clockText(triggerAtEpochMillis: Long): String {
    val unknown = stringResource(R.string.ringing_time_unknown)
    if (triggerAtEpochMillis <= 0L) return unknown
    return Instant.ofEpochMilli(triggerAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(clockFormatter)
}

@Preview(name = "Ringing light", showBackground = true, widthDp = 360, heightDp = 780, locale = "ru")
@Composable
private fun RingingScreenLightPreview() {
    ShiftAlarmTheme(darkTheme = false, useDynamicColor = false) {
        RingingScreen(sampleRingingState(), {}, {})
    }
}

@Preview(name = "Ringing dark", showBackground = true, widthDp = 360, heightDp = 780, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RingingScreenDarkPreview() {
    ShiftAlarmTheme(darkTheme = true, useDynamicColor = false) {
        RingingScreen(sampleRingingState(), {}, {})
    }
}

@Preview(name = "Ringing landscape", showBackground = true, widthDp = 800, heightDp = 360, locale = "ru")
@Composable
private fun RingingScreenLandscapePreview() {
    ShiftAlarmTheme(darkTheme = false, useDynamicColor = false) {
        RingingScreen(sampleRingingState(), {}, {})
    }
}

@Preview(name = "Ringing loading", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun RingingLoadingPreview() {
    ShiftAlarmTheme(darkTheme = false, useDynamicColor = false) {
        RingingScreen(RingingUiState.Loading, {}, {})
    }
}

@Preview(name = "Ringing loading dark", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RingingLoadingDarkPreview() {
    ShiftAlarmTheme(darkTheme = true, useDynamicColor = false) {
        RingingScreen(RingingUiState.Loading, {}, {})
    }
}

@Preview(name = "Ringing error", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun RingingErrorPreview() {
    ShiftAlarmTheme(darkTheme = false, useDynamicColor = false) {
        RingingScreen(RingingUiState.Error(UiText.Raw("Нет данных будильника")), {}, {})
    }
}

@Preview(name = "Ringing error dark", showBackground = true, widthDp = 360, heightDp = 640, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RingingErrorDarkPreview() {
    ShiftAlarmTheme(darkTheme = true, useDynamicColor = false) {
        RingingScreen(RingingUiState.Error(UiText.Raw("Нет данных будильника")), {}, {})
    }
}

private fun sampleRingingState(): RingingUiState = RingingUiState.Ringing(
    shiftName = "Ночь",
    triggerAtEpochMillis = LocalDate.of(2026, 11, 3)
        .atTime(7, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli(),
    snoozeMinutes = 10,
)
