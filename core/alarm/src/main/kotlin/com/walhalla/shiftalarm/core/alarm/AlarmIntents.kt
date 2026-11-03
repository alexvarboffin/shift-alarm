package com.walhalla.shiftalarm.core.alarm

object AlarmIntents {
    const val ACTION_FIRE: String = "com.walhalla.shiftalarm.action.ALARM_FIRE"
    const val EXTRA_TRIGGER_AT_MILLIS: String = "com.walhalla.shiftalarm.extra.TRIGGER_AT_MILLIS"
    const val EXTRA_SHIFT_NAME: String = "com.walhalla.shiftalarm.extra.SHIFT_NAME"
    const val EXTRA_SNOOZE: String = "com.walhalla.shiftalarm.extra.SNOOZE"

    const val REQUEST_FIRE: Int = 4101
    const val REQUEST_SHOW: Int = 4102
    const val REQUEST_CONTENT: Int = 4103
    const val REQUEST_STOP: Int = 4104
    const val REQUEST_SNOOZE: Int = 4105
}
