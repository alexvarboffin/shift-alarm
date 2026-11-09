package com.walhalla.shiftalarm.ui

import com.walhalla.shiftalarm.core.model.AppPermission
import com.walhalla.shiftalarm.core.model.isSatisfied
import com.walhalla.shiftalarm.core.ui.UiText

/** Kept for the current settings screen. Derived from [SettingsUiState.notificationStatus]. */
enum class NotificationPermission {
    Granted,
    Denied,
    NotRequired,
}

/** Lives in :core:model; the alias keeps existing `ui.PermissionStatus` imports working. */
typealias PermissionStatus = com.walhalla.shiftalarm.core.model.PermissionStatus

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
    /**
     * Set when notifications are DENIED_PERMANENTLY because the current ringing channel is off.
     * The button then opens ACTION_CHANNEL_NOTIFICATION_SETTINGS for this id, otherwise app notification settings.
     */
    val blockedChannelId: String? = null,
) {
    val exactAlarm: Boolean get() = exactAlarmStatus.isSatisfied
    val fullScreenIntent: Boolean get() = fullScreenIntentStatus.isSatisfied
    val canScheduleExactAlarms: Boolean get() = exactAlarm
    val canUseFullScreenIntent: Boolean get() = fullScreenIntent
    val notificationPermission: NotificationPermission
        get() = when (notificationStatus) {
            PermissionStatus.GRANTED -> NotificationPermission.Granted
            PermissionStatus.NOT_REQUIRED -> NotificationPermission.NotRequired
            else -> NotificationPermission.Denied
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

/**
 * Notifications. Below API 33 there is no runtime dialog: [NOT_REQUIRED][PermissionStatus.NOT_REQUIRED]
 * while notifications are on, [DENIED_PERMANENTLY][PermissionStatus.DENIED_PERMANENTLY] (button to
 * notification settings) when the user switched them off.
 */
fun notificationStatus(
    sdkInt: Int,
    enabled: Boolean,
    granted: Boolean,
    requested: Boolean,
    rationale: Boolean,
    channelOff: Boolean = false,
): PermissionStatus {
    val base = when {
        sdkInt < 33 -> if (enabled) PermissionStatus.NOT_REQUIRED else PermissionStatus.DENIED_PERMANENTLY
        else -> permissionStatus(granted, requested, rationale)
    }
    // Granted is not enough: app notifications or the ringing channel can be off in system settings.
    return if (base.isSatisfied && (!enabled || channelOff)) PermissionStatus.DENIED_PERMANENTLY else base
}

/** Special access (exact alarm from API 31, full-screen intent from API 34): no dialog, only settings. */
fun specialAccessStatus(sdkInt: Int, since: Int, granted: Boolean, requested: Boolean): PermissionStatus = when {
    sdkInt < since -> PermissionStatus.NOT_REQUIRED
    else -> permissionStatus(granted, requested, rationale = false)
}

sealed interface SettingsEvent {
    data class SelectSnooze(val minutes: Int) : SettingsEvent

    data class SetUseDynamicColor(val enabled: Boolean) : SettingsEvent

    /** Send right before the system dialog or settings screen opens. */
    data class PermissionRequested(val permission: AppPermission) : SettingsEvent

    /** Clears the error and reads settings again. */
    data object Retry : SettingsEvent
}
