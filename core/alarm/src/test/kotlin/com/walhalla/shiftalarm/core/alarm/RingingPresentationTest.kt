package com.walhalla.shiftalarm.core.alarm

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RingingPresentationTest {
    @Test
    fun deniedFullScreenIntentPostsHeadsUpWithoutFullScreen() {
        val notification = ringing(fullScreen = false)
        assertNull(notification.fullScreenIntent)
        @Suppress("DEPRECATION")
        val priority = notification.priority
        assertEquals(NotificationCompat.PRIORITY_MAX, priority)
        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
    }

    @Test
    fun allowedFullScreenIntentAttachesTheContentPendingIntent() {
        val notification = ringing(fullScreen = true)
        assertNotNull(notification.fullScreenIntent)
    }

    private fun ringing(fullScreen: Boolean): android.app.Notification {
        val context = RuntimeEnvironment.getApplication()
        val content = PendingIntent.getActivity(
            context,
            1,
            Intent(context, android.app.Activity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, "ring")
            .setSmallIcon(R.drawable.ic_stat_shift)
            .setContentTitle("Ночь")
            .presentRinging(content, fullScreen)
            .build()
    }
}
