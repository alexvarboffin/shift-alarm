package com.walhalla.shiftalarm.core.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingTimeoutTest {
    private fun clockAt(epochMillis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

    @Test
    fun autoStopDurationIsTenMinutes() {
        assertEquals(10 * 60 * 1000L, RingTimeout.DURATION_MILLIS)
    }

    @Test
    fun timeoutTriggersSnooze() {
        val now = 1_000_000L
        val decision = RingTimeout.decide(RingEndReason.Timeout, clockAt(now), snoozeMinutes = 5)
        assertEquals(
            RingEndDecision.Snooze(atEpochMillis = now + 5 * 60_000L, minutes = 5),
            decision,
        )
    }

    @Test
    fun repeatedTimeoutsKeepSnoozingWithNoCap() {
        var now = 0L
        repeat(50) {
            val decision = RingTimeout.decide(RingEndReason.Timeout, clockAt(now), snoozeMinutes = 10)
            assertTrue(decision is RingEndDecision.Snooze)
            val snooze = decision as RingEndDecision.Snooze
            assertEquals(now + 10 * 60_000L, snooze.atEpochMillis)
            now = snooze.atEpochMillis + RingTimeout.DURATION_MILLIS
        }
    }

    @Test
    fun userStopAfterTimeoutCancelsTheChain() {
        val timeout = RingTimeout.decide(RingEndReason.Timeout, clockAt(1_000L), snoozeMinutes = 10)
        assertTrue(timeout is RingEndDecision.Snooze)
        val afterSnooze = (timeout as RingEndDecision.Snooze).atEpochMillis + 1
        val stop = RingTimeout.decide(RingEndReason.UserStop, clockAt(afterSnooze), snoozeMinutes = 10)
        assertEquals(RingEndDecision.StopAndScheduleNext, stop)
    }

    @Test
    fun snoozeTimeIsNowPlusConfiguredMinutes() {
        val now = 1_700_000_000_000L
        val decision = RingTimeout.decide(RingEndReason.Timeout, clockAt(now), snoozeMinutes = 15)
        assertEquals(
            RingEndDecision.Snooze(atEpochMillis = now + 15 * 60_000L, minutes = 15),
            decision,
        )
    }
}
