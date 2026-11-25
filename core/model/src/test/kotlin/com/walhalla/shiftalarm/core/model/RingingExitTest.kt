package com.walhalla.shiftalarm.core.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingingExitTest {
    private fun clockAt(epochMillis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

    /**
     * Production order: clear the flag first, then the caller may startService / schedule.
     */
    private fun endRing(
        reason: RingEndReason,
        state: MutableStateFlow<Boolean>,
        clock: Clock,
        snoozeMinutes: Int = 10,
    ): RingEndDecision {
        RingingFlag.reset(state)
        return RingTimeout.decide(reason, clock, snoozeMinutes)
    }

    @Test
    fun afterStopRingStateIsOffAndResumeDoesNotRelaunch() {
        val state = MutableStateFlow(true)
        val events = mutableListOf<String>()
        val decision = endRing(RingEndReason.UserStop, state, clockAt(1_000L))
        events += "reset"
        events += "scheduler"
        assertEquals(RingEndDecision.StopAndScheduleNext, decision)
        assertFalse(state.value)
        assertFalse(shouldOpenRingingOnResume(state.value))
        assertEquals(listOf("reset", "scheduler"), events)
    }

    @Test
    fun afterSnoozeRingStateIsOffAndResumeDoesNotRelaunch() {
        val state = MutableStateFlow(true)
        val now = 5_000L
        val decision = endRing(RingEndReason.UserSnooze, state, clockAt(now), snoozeMinutes = 10)
        assertEquals(RingEndDecision.Snooze(atEpochMillis = now + 10 * 60_000L, minutes = 10), decision)
        assertFalse(state.value)
        assertFalse(shouldOpenRingingOnResume(state.value))
    }

    @Test
    fun afterTimeoutRingStateIsOffAndResumeDoesNotRelaunch() {
        val state = MutableStateFlow(true)
        val now = RingTimeout.DURATION_MILLIS
        val decision = endRing(RingEndReason.Timeout, state, clockAt(now), snoozeMinutes = 10)
        assertEquals(RingEndDecision.Snooze(atEpochMillis = now + 10 * 60_000L, minutes = 10), decision)
        assertFalse(state.value)
        assertFalse(shouldOpenRingingOnResume(state.value))
    }

    @Test
    fun timeoutIsDueExactlyAtTenMinutes() {
        assertFalse(RingTimeout.isDue(RingTimeout.DURATION_MILLIS - 1))
        assertTrue(RingTimeout.isDue(RingTimeout.DURATION_MILLIS))
        val atTen = RingTimeout.onTimeoutTick(
            stillRinging = true,
            clock = clockAt(RingTimeout.DURATION_MILLIS),
            snoozeMinutes = 10,
            elapsedMillis = RingTimeout.DURATION_MILLIS,
        )
        assertTrue(atTen is RingEndDecision.Snooze)
    }

    @Test
    fun stopBeforeTimeoutDoesNotAutoSnooze() {
        val state = MutableStateFlow(true)
        endRing(RingEndReason.UserStop, state, clockAt(9 * 60 * 1000L))
        val tick = RingTimeout.onTimeoutTick(
            stillRinging = state.value,
            clock = clockAt(RingTimeout.DURATION_MILLIS),
            snoozeMinutes = 10,
            elapsedMillis = RingTimeout.DURATION_MILLIS,
        )
        assertNull(tick)
        assertFalse(shouldOpenRingingOnResume(state.value))
    }

    @Test
    fun snoozeBeforeTimeoutDoesNotAutoSnooze() {
        val state = MutableStateFlow(true)
        val snooze = endRing(RingEndReason.UserSnooze, state, clockAt(9 * 60 * 1000L))
        assertTrue(snooze is RingEndDecision.Snooze)
        val tick = RingTimeout.onTimeoutTick(
            stillRinging = state.value,
            clock = clockAt(RingTimeout.DURATION_MILLIS),
            snoozeMinutes = 10,
            elapsedMillis = RingTimeout.DURATION_MILLIS,
        )
        assertNull(tick)
        assertFalse(shouldOpenRingingOnResume(state.value))
    }
}
