package com.walhalla.shiftalarm.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.alarm.AlarmPermissionIntents
import com.walhalla.shiftalarm.core.ui.ShiftPane
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsScreen(
        state = state,
        notificationPermission = state.notificationPermission,
        onSnoozeSelected = { viewModel.onEvent(SettingsEvent.SelectSnooze(it)) },
        onVibration = viewModel::setVibrationEnabled,
        onDynamicColor = { viewModel.onEvent(SettingsEvent.SetUseDynamicColor(it)) },
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRequestExactAlarm = {
            AlarmPermissionIntents.requestExactAlarm(appContext)?.let(appContext::startActivity)
        },
        onRequestFullScreen = {
            AlarmPermissionIntents.manageFullScreenIntent(appContext)?.let(appContext::startActivity)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    notificationPermission: NotificationPermission,
    onSnoozeSelected: (Int) -> Unit,
    onVibration: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
) {
    ShiftPane(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
            return@ShiftPane
        }
        state.errorMessage?.let { message ->
            Text(message.resolve(LocalContext.current), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        Text("Отложить на", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.snoozeOptions.forEach { minutes ->
                Button(
                    onClick = { onSnoozeSelected(minutes) },
                    enabled = state.selectedSnooze != minutes,
                ) {
                    Text("$minutes мин")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Вибрация", modifier = Modifier.weight(1f))
            Switch(checked = state.vibrationEnabled, onCheckedChange = onVibration)
        }
        Text(
            text = "Вибрация выбирает канал уведомления: с вибрацией или без. Звук — системный будильник.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Цвет системы", modifier = Modifier.weight(1f))
            Switch(checked = state.useDynamicColor, onCheckedChange = onDynamicColor)
        }
        Text(
            text = "На Android 12 и новее тема берёт цвета обоев. Ниже остаётся своя палитра.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Text("Уведомления", style = MaterialTheme.typography.titleMedium)
        Text(
            when (notificationPermission) {
                NotificationPermission.Granted -> "Разрешено"
                NotificationPermission.Denied -> "Не разрешено"
                NotificationPermission.NotRequired -> "Не требуется до Android 13"
            },
        )
        if (notificationPermission == NotificationPermission.Denied) {
            Button(onClick = onRequestNotifications, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Разрешить")
            }
        }
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Точный будильник",
            granted = state.exactAlarm,
            detail = "Без него смена не прозвенит вовремя.",
            actionLabel = "Открыть настройки",
            onAction = onRequestExactAlarm,
        )
        PermissionRow(
            title = "Показ на заблокированном экране",
            granted = state.fullScreenIntent,
            detail = "Нужен, чтобы экран звонка открылся сам.",
            actionLabel = "Открыть настройки",
            onAction = onRequestFullScreen,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    detail: String? = null,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(if (granted) "Разрешено" else "Не разрешено")
    if (detail != null) {
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
    if (!granted) {
        Button(onClick = onAction, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(actionLabel)
        }
    }
    Spacer(Modifier.height(12.dp))
}

