package com.walhalla.shiftalarm.core.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RingingChannelTest {
    @Test
    fun oneRingChannelAndTheOldOnesAreLegacy() {
        assertEquals("shift_alarm_ring", RingingChannels.RING)
        assertEquals(listOf("shift_alarm_ring_vibrate", "shift_alarm_ring_quiet"), RingingChannels.LEGACY)
        assertFalse(RingingChannels.RING in RingingChannels.LEGACY)
    }

    @Test
    fun createsRingAndDeletesLegacyOnApi28() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("shift_alarm_ring_vibrate", "old", NotificationManager.IMPORTANCE_HIGH),
        )
        RingingChannels.ensureCreated(app, sdkInt = 28)
        assertNotNull(manager.getNotificationChannel(RingingChannels.RING))
        assertNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
        assertNull(manager.getNotificationChannel("shift_alarm_ring_vibrate"))
        assertNull(manager.getNotificationChannel(RingingChannels.RING).sound)
    }

    @Test
    fun createsFallbackOnApi31() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        RingingChannels.ensureCreated(app, sdkInt = 31)
        assertNotNull(manager.getNotificationChannel(RingingChannels.RING))
        assertNotNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
    }

    @Test
    fun createsFallbackOnApi32() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        RingingChannels.ensureCreated(app, sdkInt = 32)
        assertNotNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
    }

    @Test
    fun doesNotCreateFallbackOnApi33() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        RingingChannels.ensureCreated(app, sdkInt = 33)
        assertNotNull(manager.getNotificationChannel(RingingChannels.RING))
        assertNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
    }

    @Test
    fun doesNotOverwriteAnExistingChannel() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(RingingChannels.RING, "ring", NotificationManager.IMPORTANCE_NONE),
        )
        RingingChannels.ensureCreated(app, sdkInt = 28)
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(RingingChannels.RING).importance,
        )
    }
}
