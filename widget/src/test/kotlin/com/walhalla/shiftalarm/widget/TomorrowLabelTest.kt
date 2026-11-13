package com.walhalla.shiftalarm.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TomorrowLabelTest {
    @Test
    fun clockChangesCoverZoneAndTimeSet() {
        assertEquals(true, isClockChangeAction("android.intent.action.TIMEZONE_CHANGED"))
        assertEquals(true, isClockChangeAction("android.intent.action.TIME_SET"))
        assertEquals(false, isClockChangeAction("android.intent.action.BOOT_COMPLETED"))
        assertEquals(false, isClockChangeAction(null))
    }
}
