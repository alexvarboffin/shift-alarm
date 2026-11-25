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

    /**
     * How this ring ends.
     *
     * [RingEndReason.Timeout] and [RingEndReason.UserSnooze] both arm the next ring at
     * now + the configured snooze. There is no cap and no silent end: every timeout
     * auto-snoozes again («совсем затихать нельзя») until the user presses Stop
     * ([RingEndReason.UserStop] → [RingEndDecision.StopAndScheduleNext]).
     */
    fun decide(reason: RingEndReason, clock: Clock, snoozeMinutes: Int): RingEndDecision {
        return when (reason) {
            RingEndReason.UserStop -> RingEndDecision.StopAndScheduleNext
            RingEndReason.Timeout, RingEndReason.UserSnooze -> {
                val minutes = clampedSnoozeMinutes(snoozeMinutes)
                RingEndDecision.Snooze(atEpochMillis = snoozeAt(clock, minutes), minutes = minutes)
            }
        }
    }

    /** True at exactly 10 minutes and later. */
    fun isDue(elapsedMillis: Long): Boolean = elapsedMillis >= DURATION_MILLIS

    /**
     * Timeout tick. If the ring already ended (Stop or Snooze), returns null — no auto-snooze.
     * If still ringing and [isDue], auto-snoozes. The chain has no cap.
     */
    fun onTimeoutTick(
        stillRinging: Boolean,
        clock: Clock,
        snoozeMinutes: Int,
        elapsedMillis: Long,
    ): RingEndDecision? {
        if (!stillRinging || !isDue(elapsedMillis)) return null
        return decide(RingEndReason.Timeout, clock, snoozeMinutes)
    }
}
