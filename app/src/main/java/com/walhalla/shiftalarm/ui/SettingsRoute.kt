package com.walhalla.shiftalarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.R
import com.walhalla.shiftalarm.core.alarm.AlarmPermissionIntents
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.core.ui.ShiftPane
import com.walhalla.shiftalarm.core.ui.UiText

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationDenials by rememberSaveable { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) notificationDenials = 0 else notificationDenials += 1
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
        onSnoozeSelected = { viewModel.onEvent(SettingsEvent.SelectSnooze(it)) },
        onVibration = viewModel::setVibrationEnabled,
        onDynamicColor = { viewModel.onEvent(SettingsEvent.SetUseDynamicColor(it)) },
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationDenials >= 2) {
                    openAppNotificationSettings(context)
                } else {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        onRequestExactAlarm = {
            AlarmPermissionIntents.requestExactAlarm(context)?.let(context::startActivity)
        },
        onRequestFullScreen = {
            AlarmPermissionIntents.manageFullScreenIntent(context)?.let(context::startActivity)
        },
        onRetry = viewModel::refreshPermissions,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSnoozeSelected: (Int) -> Unit,
    onVibration: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestFullScreen: () -> Unit,
    onRetry: () -> Unit = {},
    showDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val shown = rememberReachable(state)
    val error = shown.errorMessage
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(error) { dismissed = false }
    ShiftPane(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        if (shown.isLoading) {
            LoadingBlock(stringResource(R.string.settings_loading))
            return@ShiftPane
        }
        if (error != null && !dismissed) {
            ErrorBanner(
                message = error.resolve(LocalContext.current),
                onRetry = onRetry,
                onDismiss = { dismissed = true },
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.settings_snooze_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_snooze_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.snoozeOptions.forEach { minutes ->
                val selected = shown.selectedSnooze == minutes
                FilterChip(
                    selected = selected,
                    onClick = { onSnoozeSelected(minutes) },
                    label = { Text(stringResource(R.string.settings_snooze_minutes, minutes)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                ToggleRow(
                    label = stringResource(R.string.settings_vibration),
                    checked = shown.vibrationEnabled,
                    onCheckedChange = onVibration,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_vibration_detail),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (showDynamicColor) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    ToggleRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        checked = shown.useDynamicColor,
                        onCheckedChange = onDynamicColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_dynamic_color_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_permissions_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        PermissionCard(
            title = stringResource(R.string.settings_notifications),
            status = notificationStatus(shown.notificationPermission),
            denied = shown.notificationPermission == NotificationPermission.Denied,
            detail = stringResource(R.string.settings_notifications_detail),
            actionLabel = stringResource(R.string.settings_notifications_action),
            onAction = onRequestNotifications,
        )
        PermissionCard(
            title = stringResource(R.string.settings_exact_alarm),
            status = stringResource(
                if (shown.exactAlarm) {
                    R.string.settings_permission_granted
                } else {
                    R.string.settings_permission_denied
                },
            ),
            denied = !shown.exactAlarm,
            detail = stringResource(R.string.settings_exact_alarm_detail),
            actionLabel = stringResource(R.string.settings_open_settings),
            onAction = onRequestExactAlarm,
        )
        PermissionCard(
            title = stringResource(R.string.settings_fullscreen),
            status = stringResource(
                if (shown.fullScreenIntent) {
                    R.string.settings_permission_granted
                } else {
                    R.string.settings_permission_denied
                },
            ),
            denied = !shown.fullScreenIntent,
            detail = stringResource(R.string.settings_fullscreen_detail),
            actionLabel = stringResource(R.string.settings_open_settings),
            onAction = onRequestFullScreen,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun notificationStatus(permission: NotificationPermission): String = stringResource(
    when (permission) {
        NotificationPermission.Granted -> R.string.settings_permission_granted
        NotificationPermission.Denied -> R.string.settings_permission_denied
        NotificationPermission.NotRequired -> R.string.settings_permission_not_required
    },
)

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun rememberReachable(state: SettingsUiState): SettingsUiState {
    var saved by remember { mutableStateOf<SettingsUiState?>(null) }
    if (!state.isLoading && state.errorMessage == null) saved = state
    val previous = saved
    return if (state.errorMessage != null && previous != null) {
        previous.copy(errorMessage = state.errorMessage)
    } else {
        state
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_retry))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_dismiss))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    status: String,
    denied: Boolean,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
            if (denied) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
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

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Preview(name = "Settings light", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun SettingsScreenLightPreview() {
    PreviewFrame(false) { SettingsBody(sampleSettingsState()) }
}

@Preview(name = "Settings dark", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenDarkPreview() {
    PreviewFrame(true) { SettingsBody(sampleSettingsState()) }
}

@Preview(name = "Settings granted", showBackground = true, widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun SettingsScreenGrantedPreview() {
    PreviewFrame(false) {
        SettingsBody(
            sampleSettingsState().copy(
                notificationPermission = NotificationPermission.Granted,
                exactAlarm = true,
                fullScreenIntent = true,
            ),
        )
    }
}

@Preview(name = "Settings not required", showBackground = true, widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun SettingsScreenNotRequiredPreview() {
    PreviewFrame(false) {
        SettingsBody(
            sampleSettingsState().copy(
                notificationPermission = NotificationPermission.NotRequired,
                exactAlarm = true,
                fullScreenIntent = true,
            ),
        )
    }
}

@Preview(name = "Settings loading", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru")
@Composable
private fun SettingsScreenLoadingPreview() {
    PreviewFrame(false) { SettingsBody(SettingsUiState(isLoading = true)) }
}

@Preview(name = "Settings loading dark", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenLoadingDarkPreview() {
    PreviewFrame(true) { SettingsBody(SettingsUiState(isLoading = true)) }
}

@Preview(name = "Settings error", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun SettingsScreenErrorPreview() {
    PreviewFrame(false) {
        SettingsBody(sampleSettingsState().copy(errorMessage = UiText.Raw("Настройки недоступны")))
    }
}

@Preview(name = "Settings error dark", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenErrorDarkPreview() {
    PreviewFrame(true) {
        SettingsBody(sampleSettingsState().copy(errorMessage = UiText.Raw("Настройки недоступны")))
    }
}

@Composable
private fun SettingsBody(state: SettingsUiState) {
    SettingsScreen(
        state = state,
        onSnoozeSelected = {},
        onVibration = {},
        onDynamicColor = {},
        onRequestNotifications = {},
        onRequestExactAlarm = {},
        onRequestFullScreen = {},
        showDynamicColor = true,
    )
}

@Composable
private fun PreviewFrame(darkTheme: Boolean, content: @Composable () -> Unit) {
    ShiftAlarmTheme(darkTheme = darkTheme, useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun sampleSettingsState(): SettingsUiState = SettingsUiState(
    isLoading = false,
    snoozeOptions = SettingsUiState.SNOOZE_OPTIONS,
    selectedSnooze = 10,
    vibrationEnabled = true,
    useDynamicColor = true,
    notificationPermission = NotificationPermission.Denied,
    exactAlarm = false,
    fullScreenIntent = false,
)
