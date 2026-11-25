package com.walhalla.shiftalarm.core.model

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single reset of the ringing flag. All three exits — Stop, Snooze, and timeout
 * auto-snooze — must call this before `startService` or scheduler work, so a later
 * Activity resume cannot see a stale true and reopen the ringing screen.
 */
object RingingFlag {
    fun reset(isRinging: MutableStateFlow<Boolean>) {
        isRinging.value = false
    }
}

/**
 * [MainActivity] must not open the ringing screen. That screen is opened only by
 * the full-screen intent or the ringing notification.
 */
fun shouldOpenRingingOnResume(isRinging: Boolean): Boolean = false
