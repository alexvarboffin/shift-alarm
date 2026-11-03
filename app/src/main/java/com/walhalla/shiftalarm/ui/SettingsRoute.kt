package com.walhalla.shiftalarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.core.alarm.AlarmPermissionIntents
import com.walhalla.shiftalarm.core.ui.ShiftPane

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                notificationsGranted = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsScreen(
        state = state,
        notificationsGranted = notificationsGranted,
        onSnoozeSelected = viewModel::setSnoozeMinutes,
        onVibration = viewModel::setVibrationEnabled,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRequestExactAlarm = {
            AlarmPermissionIntents.requestExactAlarm(context)?.let(context::startActivity)
        },
        onRequestFullScreen = {
            AlarmPermissionIntents.manageFullScreenIntent(context)?.let(context::startActivity)
        },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    notificationsGranted: Boolean,
    onSnoozeSelected: (Int) -> Unit,
    onVibration: (Boolean) -> Unit,
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
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        Text("Отложить на", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(vertical = 8.dp)) {
            listOf(5, 10, 15, 30).forEach { minutes ->
                Button(
                    onClick = { onSnoozeSelected(minutes) },
                    modifier = Modifier.padding(end = 8.dp),
                    enabled = state.snoozeMinutes != minutes,
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
        Spacer(Modifier.height(16.dp))
        PermissionRow(
            title = "Уведомления",
            granted = notificationsGranted,
            actionLabel = "Разрешить",
            onAction = onRequestNotifications,
        )
        PermissionRow(
            title = "Точный будильник",
            granted = state.canScheduleExactAlarms,
            detail = "Без него смена не прозвенит вовремя.",
            actionLabel = "Открыть настройки",
            onAction = onRequestExactAlarm,
        )
        PermissionRow(
            title = "Показ на заблокированном экране",
            granted = state.canUseFullScreenIntent,
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

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
