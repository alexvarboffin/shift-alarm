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
class RingingChannelTest {
    @Test
    fun oneRingChannelAndTheOldOnesAreLegacy() {
        assertEquals("shift_alarm_ring", RingingChannels.RING)
        assertEquals(listOf("shift_alarm_ring_vibrate", "shift_alarm_ring_quiet"), RingingChannels.LEGACY)
        assertFalse(RingingChannels.RING in RingingChannels.LEGACY)
    }

    @Test
    @Config(sdk = [28])
    fun createsRingAndDeletesLegacyOnApi28() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("shift_alarm_ring_vibrate", "old", NotificationManager.IMPORTANCE_HIGH),
        )
        RingingChannels.ensureCreated(app)
        assertNotNull(manager.getNotificationChannel(RingingChannels.RING))
        assertNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
        assertNull(manager.getNotificationChannel("shift_alarm_ring_vibrate"))
        assertNull(manager.getNotificationChannel(RingingChannels.RING).sound)
    }

    @Test
    @Config(sdk = [31])
    fun createsFallbackOnApi31() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        RingingChannels.ensureCreated(app)
        assertNotNull(manager.getNotificationChannel(RingingChannels.RING))
        assertNotNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
    }

    @Test
    @Config(sdk = [32])
    fun createsFallbackOnApi32() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        RingingChannels.ensureCreated(app)
        assertNotNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
    }

    @Test
    @Config(sdk = [33])
    fun doesNotCreateFallbackOnApi33() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        RingingChannels.ensureCreated(app)
        assertNotNull(manager.getNotificationChannel(RingingChannels.RING))
        assertNull(manager.getNotificationChannel(RingingChannels.FALLBACK))
    }

    @Test
    @Config(sdk = [28])
    fun doesNotOverwriteAnExistingChannel() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(RingingChannels.RING, "ring", NotificationManager.IMPORTANCE_NONE),
        )
        RingingChannels.ensureCreated(app)
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(RingingChannels.RING).importance,
        )
    }
}
