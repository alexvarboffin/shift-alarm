package com.walhalla.shiftalarm.core.model

/** Permissions the app asks for. Stored by name in [SettingsRepository.permissionRequestCounts]. */
enum class AppPermission {
    NOTIFICATIONS,
    EXACT_ALARM,
    FULL_SCREEN_INTENT,
}
