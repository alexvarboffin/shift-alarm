package com.walhalla.shiftalarm.core.alarm

/**
 * Arms exactly one alarm: the next shift, or a snooze that replaced it.
 * Callers reschedule after the alarm fires, after boot, and after the clock changes.
 * The AlarmManager implementation lives in this module.
 */
interface AlarmScheduler {
    suspend fun scheduleNext(nowEpochMillis: Long = System.currentTimeMillis())

    suspend fun cancelNext()

    suspend fun snooze(minutes: Int, shiftName: String)

    suspend fun dismiss()

    fun canScheduleExactAlarms(): Boolean

    fun canUseFullScreenIntent(): Boolean
}

/** Starts or updates the ringing foreground service. UI and the notification share this. */
interface RingingController {
    /** True from the moment the ring starts until Stop, Snooze or the 10-minute auto-stop. */
    val isRinging: kotlinx.coroutines.flow.StateFlow<Boolean>

    fun stop()

    fun snooze()
}

/** Explicit intent that opens the ringing screen. Implemented in `:feature:alarm`. */
interface RingingLaunchIntent {
    fun create(shiftName: String, triggerAtEpochMillis: Long, snooze: Boolean): android.content.Intent
}
