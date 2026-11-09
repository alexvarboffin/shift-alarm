package com.walhalla.shiftalarm.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RingingChannelTest {
    @Test
    fun oneRingChannelAndTheOldOnesAreLegacy() {
        assertEquals("shift_alarm_ring", RingingChannels.RING)
        assertEquals(listOf("shift_alarm_ring_vibrate", "shift_alarm_ring_quiet"), RingingChannels.LEGACY)
        assertFalse(RingingChannels.RING in RingingChannels.LEGACY)
    }
}
