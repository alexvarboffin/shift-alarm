package com.walhalla.shiftalarm.core.model

import java.time.Clock

/** Why the current ring is ending. */
enum class RingEndReason {
    Timeout,
    UserStop,
    UserSnooze,
}

sealed interface RingEndDecision {
    /** Next ring at [atEpochMillis], via the single scheduler snooze path. */
    data class Snooze(val atEpochMillis: Long, val minutes: Int) : RingEndDecision

    /** Clear the snooze chain and arm the next regular shift alarm. */
    data object StopAndScheduleNext : RingEndDecision
}

/**
 * Pure ringing end policy. Duration and "timeout -> snooze" live here.
 * The service only executes [decide]. Tests use a fake [Clock]; no real time.
 */
object RingTimeout {
    const val DURATION_MILLIS: Long = 10 * 60 * 1000L

    const val MIN_SNOOZE_MINUTES: Int = 1
    const val MAX_SNOOZE_MINUTES: Int = 120

    fun clampedSnoozeMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_SNOOZE_MINUTES, MAX_SNOOZE_MINUTES)

    fun snoozeAt(clock: Clock, snoozeMinutes: Int): Long {
        val minutes = clampedSnoozeMinutes(snoozeMinutes)
        return clock.millis() + minutes * 60_000L
    }

    fun decide(reason: RingEndReason, clock: Clock, snoozeMinutes: Int): RingEndDecision {
        return when (reason) {
            RingEndReason.UserStop -> RingEndDecision.StopAndScheduleNext
            RingEndReason.Timeout, RingEndReason.UserSnooze -> {
                val minutes = clampedSnoozeMinutes(snoozeMinutes)
                RingEndDecision.Snooze(atEpochMillis = snoozeAt(clock, minutes), minutes = minutes)
            }
        }
    }
}
