package com.walhalla.shiftalarm.core.alarm

import android.app.AlarmManager
import android.content.Intent
import com.walhalla.shiftalarm.core.model.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RingingSnoozeTest {
    @Test
    fun lockedSnoozeDoesNotReadCredentialSettings() = runBlocking {
        var reads = 0
        val minutes = resolveSnoozeMinutes(userUnlocked = false) {
            reads += 1
            20
        }
        assertEquals(UserSettings.DEFAULT_SNOOZE_MINUTES, minutes)
        assertEquals(0, reads)
    }

    @Test
    fun unlockedSnoozeReadsTheSelectedDuration() = runBlocking {
        val minutes = resolveSnoozeMinutes(userUnlocked = true) { 20 }
        assertEquals(20, minutes)
    }

    @Test
    fun stopRingingRunsWhenSnoozeFails() = runBlocking {
        var stopped = false
        var failed = false
        try {
            runThenStopRinging(stop = { stopped = true }) {
                throw IllegalStateException("schedule failed")
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertTrue(stopped)
    }

    @Test
    fun notBeforeIsTheLaterOfFireAndNow() {
        assertEquals(20L, notBeforeAfterFire(firedAt = 10L, now = 20L))
        assertEquals(30L, notBeforeAfterFire(firedAt = 30L, now = 20L))
    }

    @Test
    fun exactAlarmReceiverIgnoresActionsOutsideTheAllowList() {
        assertTrue(isExactAlarmPermissionAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        assertEquals(false, isExactAlarmPermissionAction(null))
        assertEquals(false, isExactAlarmPermissionAction(Intent.ACTION_BOOT_COMPLETED))
    }
}
