package com.walhalla.shiftalarm.ui

import com.walhalla.shiftalarm.core.model.AppPermission
import com.walhalla.shiftalarm.core.ui.UiText

/** Kept for the current settings screen. Derived from [SettingsUiState.notificationStatus]. */
enum class NotificationPermission {
    Granted,
    Denied,
    NotRequired,
}

/**
 * What the UI should do for one permission.
 * [DENIED_PERMANENTLY]: the system dialog will not show again, the button opens system settings.
 */
enum class PermissionStatus {
    GRANTED,
    NOT_ASKED,
    CAN_ASK_AGAIN,
    DENIED_PERMANENTLY,
}

/**
 * [rationale] is `shouldShowRequestPermissionRationale` from the Activity.
 * Exact alarm and full-screen intent have no runtime dialog, so they pass false:
 * once asked and still missing, they go to system settings.
 */
fun permissionStatus(granted: Boolean, requested: Boolean, rationale: Boolean): PermissionStatus = when {
    granted -> PermissionStatus.GRANTED
    rationale -> PermissionStatus.CAN_ASK_AGAIN
    requested -> PermissionStatus.DENIED_PERMANENTLY
    else -> PermissionStatus.NOT_ASKED
}

/** Nothing is granted until a real check says so. */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val snoozeOptions: List<Int> = SNOOZE_OPTIONS,
    val selectedSnooze: Int = 10,
    val vibrationEnabled: Boolean = true,
    val useDynamicColor: Boolean = true,
    val notificationStatus: PermissionStatus = PermissionStatus.NOT_ASKED,
    val exactAlarmStatus: PermissionStatus = PermissionStatus.NOT_ASKED,
    val fullScreenIntentStatus: PermissionStatus = PermissionStatus.NOT_ASKED,
    val errorMessage: UiText? = null,
) {
    val exactAlarm: Boolean get() = exactAlarmStatus == PermissionStatus.GRANTED
    val fullScreenIntent: Boolean get() = fullScreenIntentStatus == PermissionStatus.GRANTED
    val canScheduleExactAlarms: Boolean get() = exactAlarm
    val canUseFullScreenIntent: Boolean get() = fullScreenIntent
    val notificationPermission: NotificationPermission
        get() = if (notificationStatus == PermissionStatus.GRANTED) {
            NotificationPermission.Granted
        } else {
            NotificationPermission.Denied
        }

    fun status(permission: AppPermission): PermissionStatus = when (permission) {
        AppPermission.NOTIFICATIONS -> notificationStatus
        AppPermission.EXACT_ALARM -> exactAlarmStatus
        AppPermission.FULL_SCREEN_INTENT -> fullScreenIntentStatus
    }

    companion object {
        val SNOOZE_OPTIONS: List<Int> = listOf(5, 10, 15, 20, 30)
    }
}

sealed interface SettingsEvent {
    data class SelectSnooze(val minutes: Int) : SettingsEvent

    data class SetUseDynamicColor(val enabled: Boolean) : SettingsEvent

    /** Send right before the system dialog or settings screen opens. */
    data class PermissionRequested(val permission: AppPermission) : SettingsEvent

    /** Clears the error and reads settings again. */
    data object Retry : SettingsEvent
}
