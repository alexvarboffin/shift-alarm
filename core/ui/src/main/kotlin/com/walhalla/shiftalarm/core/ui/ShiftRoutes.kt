package com.walhalla.shiftalarm.core.ui

/**
 * NavHost routes. Ringing is not here: it is [com.walhalla.shiftalarm.feature.alarm.RingingActivity],
 * opened by the full-screen intent.
 */
object ShiftRoutes {
    const val SCHEDULE: String = "schedule"
    const val CALENDAR: String = "calendar"
    const val SETTINGS: String = "settings"
}
