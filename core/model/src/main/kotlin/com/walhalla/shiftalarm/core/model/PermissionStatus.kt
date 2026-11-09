package com.walhalla.shiftalarm.core.model

/**
 * What the UI should do for one permission.
 * [DENIED_PERMANENTLY]: the system dialog will not show again, the button opens system settings.
 * [NOT_REQUIRED]: this OS version has no runtime permission for it. No button.
 */
enum class PermissionStatus {
    GRANTED,
    NOT_REQUIRED,
    NOT_ASKED,
    CAN_ASK_AGAIN,
    DENIED_PERMANENTLY,
}

/** The app may rely on it: granted, or the OS does not ask for it. */
val PermissionStatus.isSatisfied: Boolean
    get() = this == PermissionStatus.GRANTED || this == PermissionStatus.NOT_REQUIRED
