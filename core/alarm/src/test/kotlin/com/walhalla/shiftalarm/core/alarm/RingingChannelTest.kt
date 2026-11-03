package com.walhalla.shiftalarm.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingingChannelTest {
    @Test
    fun vibrationUsesASeparateChannelFromQuiet() {
        assertEquals(RINGING_CHANNEL_VIBRATE, ringingChannelId(vibration = true))
        assertEquals(RINGING_CHANNEL_QUIET, ringingChannelId(vibration = false))
        assertTrue(ringingChannelId(true) != ringingChannelId(false))
    }
}
