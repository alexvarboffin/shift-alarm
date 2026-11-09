package com.walhalla.shiftalarm.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walhalla.shiftalarm.R
import com.walhalla.shiftalarm.core.model.AppPermission
import com.walhalla.shiftalarm.core.ui.ShiftAlarmTheme
import com.walhalla.shiftalarm.core.ui.ShiftPane
import com.walhalla.shiftalarm.core.ui.UiText

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // PermissionRequested goes out only after a real answer, judged by rationale before and after
    // the request (no timing). Denied with no rationale before or after is ambiguous: the dialog
    // was closed without an answer, or the system denied silently ("never ask again"). The first
    // such result keeps NOT_ASKED; the second one is a real answer, so the card moves to settings.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    var ambiguousDenials by rememberSaveable { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val rationaleAfter = notificationRationale(activity)
        val ambiguous = !granted && rationaleAfter != true && !rationaleBefore
        if (ambiguous) ambiguousDenials++ else ambiguousDenials = 0
        if (!ambiguous || ambiguousDenials >= 2) {
            viewModel.onEvent(SettingsEvent.PermissionRequested(AppPermission.NOTIFICATIONS))
        }
        viewModel.refreshPermissions(rationaleAfter)
    }
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(notificationRationale(activity))
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
        onPermissionAction = { permission, status ->
            when (status) {
                PermissionStatus.GRANTED -> Unit
                PermissionStatus.NOT_ASKED, PermissionStatus.CAN_ASK_AGAIN -> {
                    if (permission != AppPermission.NOTIFICATIONS) {
                        viewModel.onEvent(SettingsEvent.PermissionRequested(permission))
                    }
                    requestPermission(context, permission) {
                        rationaleBefore = notificationRationale(activity) == true
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                PermissionStatus.DENIED_PERMANENTLY -> openPermissionSettings(context, permission)
            }
        },
        onRetry = { viewModel.onEvent(SettingsEvent.Retry) },
    )
}

/** Stateless: every label and button below comes from [SettingsUiState.status]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSnoozeSelected: (Int) -> Unit,
    onVibration: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPermissionAction: (AppPermission, PermissionStatus) -> Unit,
    onRetry: () -> Unit,
    showDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val shown = rememberReachable(state)
    val error = shown.errorMessage
    ShiftPane(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        if (shown.isLoading) {
            LoadingBlock(stringResource(R.string.settings_loading))
            return@ShiftPane
        }
        if (error != null) {
            ErrorBanner(
                message = error.resolve(LocalContext.current),
                onRetry = onRetry,
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
            detail = stringResource(R.string.settings_notifications_detail),
            status = shown.notificationStatus,
            onAction = { onPermissionAction(AppPermission.NOTIFICATIONS, shown.notificationStatus) },
        )
        PermissionCard(
            title = stringResource(R.string.settings_exact_alarm),
            detail = stringResource(R.string.settings_exact_alarm_detail),
            status = shown.exactAlarmStatus,
            onAction = { onPermissionAction(AppPermission.EXACT_ALARM, shown.exactAlarmStatus) },
        )
        PermissionCard(
            title = stringResource(R.string.settings_fullscreen),
            detail = stringResource(R.string.settings_fullscreen_detail),
            status = shown.fullScreenIntentStatus,
            onAction = { onPermissionAction(AppPermission.FULL_SCREEN_INTENT, shown.fullScreenIntentStatus) },
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun statusLabel(status: PermissionStatus): String = stringResource(
    when (status) {
        PermissionStatus.GRANTED -> R.string.settings_permission_granted
        PermissionStatus.NOT_ASKED -> R.string.settings_permission_not_asked
        PermissionStatus.CAN_ASK_AGAIN -> R.string.settings_permission_denied
        PermissionStatus.DENIED_PERMANENTLY -> R.string.settings_permission_denied_permanently
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
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.settings_retry))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    detail: String,
    status: PermissionStatus,
    onAction: () -> Unit,
) {
    val granted = status == PermissionStatus.GRANTED
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
                text = statusLabel(status),
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                val label = stringResource(
                    if (status == PermissionStatus.DENIED_PERMANENTLY) {
                        R.string.settings_open_settings
                    } else {
                        R.string.settings_permission_allow
                    },
                )
                val modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                if (status == PermissionStatus.DENIED_PERMANENTLY) {
                    OutlinedButton(onClick = onAction, modifier = modifier) { Text(label) }
                } else {
                    Button(onClick = onAction, modifier = modifier) { Text(label) }
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

private fun notificationRationale(activity: Activity?): Boolean? {
    if (activity == null) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

/** First tap goes straight to the system: the runtime dialog or the per-app request screen. */
private fun requestPermission(
    context: Context,
    permission: AppPermission,
    launchNotificationDialog: () -> Unit,
) {
    when (permission) {
        AppPermission.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchNotificationDialog()
        } else {
            openPermissionSettings(context, permission)
        }
        AppPermission.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startSettings(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)),
            )
        }
        AppPermission.FULL_SCREEN_INTENT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startSettings(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context)),
            )
        }
    }
}

/** The dialog will not show again: open the matching system settings screen. */
private fun openPermissionSettings(context: Context, permission: AppPermission) {
    when (permission) {
        AppPermission.NOTIFICATIONS -> context.startSettings(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
        AppPermission.EXACT_ALARM, AppPermission.FULL_SCREEN_INTENT ->
            requestPermission(context, permission) {}
    }
}

private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

/** Explicit package on every settings intent; falls back to app details if a vendor drops the screen. */
private fun Context.startSettings(intent: Intent) {
    val target = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(target)
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(this))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Preview(name = "Settings not asked", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun SettingsNotAskedPreview() {
    PreviewFrame(false) { SettingsBody(sampleSettingsState(PermissionStatus.NOT_ASKED)) }
}

@Preview(name = "Settings not asked dark", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsNotAskedDarkPreview() {
    PreviewFrame(true) { SettingsBody(sampleSettingsState(PermissionStatus.NOT_ASKED)) }
}

@Preview(name = "Settings can ask again", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun SettingsCanAskAgainPreview() {
    PreviewFrame(false) { SettingsBody(sampleSettingsState(PermissionStatus.CAN_ASK_AGAIN)) }
}

@Preview(name = "Settings can ask again dark", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsCanAskAgainDarkPreview() {
    PreviewFrame(true) { SettingsBody(sampleSettingsState(PermissionStatus.CAN_ASK_AGAIN)) }
}

@Preview(name = "Settings denied permanently", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun SettingsDeniedPermanentlyPreview() {
    PreviewFrame(false) { SettingsBody(sampleSettingsState(PermissionStatus.DENIED_PERMANENTLY)) }
}

@Preview(name = "Settings denied permanently dark", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDeniedPermanentlyDarkPreview() {
    PreviewFrame(true) { SettingsBody(sampleSettingsState(PermissionStatus.DENIED_PERMANENTLY)) }
}

@Preview(name = "Settings granted", showBackground = true, widthDp = 360, heightDp = 900, locale = "ru")
@Composable
private fun SettingsGrantedPreview() {
    PreviewFrame(false) { SettingsBody(sampleSettingsState(PermissionStatus.GRANTED)) }
}

@Preview(name = "Settings granted dark", showBackground = true, widthDp = 360, heightDp = 900, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsGrantedDarkPreview() {
    PreviewFrame(true) { SettingsBody(sampleSettingsState(PermissionStatus.GRANTED)) }
}

@Preview(name = "Settings loading", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru")
@Composable
private fun SettingsLoadingPreview() {
    PreviewFrame(false) { SettingsBody(SettingsUiState(isLoading = true)) }
}

@Preview(name = "Settings loading dark", showBackground = true, widthDp = 360, heightDp = 480, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsLoadingDarkPreview() {
    PreviewFrame(true) { SettingsBody(SettingsUiState(isLoading = true)) }
}

@Preview(name = "Settings error", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun SettingsErrorPreview() {
    PreviewFrame(false) {
        SettingsBody(sampleSettingsState(PermissionStatus.NOT_ASKED).copy(errorMessage = UiText.Res(R.string.settings_error_read)))
    }
}

@Preview(name = "Settings error dark", showBackground = true, widthDp = 360, heightDp = 1000, locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsErrorDarkPreview() {
    PreviewFrame(true) {
        SettingsBody(sampleSettingsState(PermissionStatus.NOT_ASKED).copy(errorMessage = UiText.Res(R.string.settings_error_read)))
    }
}

@Composable
private fun SettingsBody(state: SettingsUiState) {
    SettingsScreen(
        state = state,
        onSnoozeSelected = {},
        onVibration = {},
        onDynamicColor = {},
        onPermissionAction = { _, _ -> },
        onRetry = {},
        showDynamicColor = true,
    )
}

@Composable
private fun PreviewFrame(darkTheme: Boolean, content: @Composable () -> Unit) {
    ShiftAlarmTheme(darkTheme = darkTheme, useDynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun sampleSettingsState(status: PermissionStatus): SettingsUiState = SettingsUiState(
    isLoading = false,
    snoozeOptions = SettingsUiState.SNOOZE_OPTIONS,
    selectedSnooze = 10,
    vibrationEnabled = true,
    useDynamicColor = true,
    notificationStatus = status,
    exactAlarmStatus = status,
    fullScreenIntentStatus = status,
)
