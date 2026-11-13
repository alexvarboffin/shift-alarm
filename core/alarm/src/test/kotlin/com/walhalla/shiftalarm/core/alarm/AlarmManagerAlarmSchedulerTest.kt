package com.walhalla.shiftalarm.core.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.UserSettings
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmManagerAlarmSchedulerTest {
    @Test
    fun scheduleNext_setsAlarmClockForTheNextShift() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val context = RuntimeEnvironment.getApplication()
        val zone = ZoneId.systemDefault()
        val pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 1, 1))
        val scheduler = AlarmManagerAlarmScheduler(
            context,
            FakeShiftRepository(pattern),
            FakeAlarmRepository(),
            FakeSettingsRepository(),
            DeviceProtectedAlarmStore.create(File(context.cacheDir, "exact-on.preferences_pb")),
        )
        val notBefore = LocalDateTime.of(2026, 1, 1, 5, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        scheduler.scheduleNext(notBefore)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).scheduledAlarms.single()
        val expected = LocalDateTime.of(2026, 1, 1, 5, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, scheduled.triggerAtMs)
        assertNotNull(scheduled.alarmClockInfo)
    }

    @Test
    fun scheduleNext_withoutExactAlarmUsesInexactFallbackAndWarns() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val zone = ZoneId.systemDefault()
        val pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 1, 1))
        val scheduler = AlarmManagerAlarmScheduler(
            context,
            FakeShiftRepository(pattern),
            FakeAlarmRepository(),
            FakeSettingsRepository(),
            DeviceProtectedAlarmStore.create(File(context.cacheDir, "exact-off.preferences_pb")),
        )
        val notBefore = LocalDateTime.of(2026, 1, 1, 5, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        scheduler.scheduleNext(notBefore)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).scheduledAlarms.single()
        assertNull(scheduled.alarmClockInfo)
        assertTrue(scheduled.isAllowWhileIdle || scheduled.windowLengthMs > 0)
        val notification = shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(AlarmIntents.NOTIFICATION_EXACT_DENIED)
        assertEquals(
            "Точные будильники выключены",
            notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
    }
}

private class FakeShiftRepository(
    private val pattern: ShiftPattern,
) : ShiftRepository {
    override fun observePatterns(): Flow<List<ShiftPattern>> = flowOf(listOf(pattern))

    override fun observeActivePattern(): Flow<ShiftPattern?> = flowOf(pattern)

    override fun observeShiftTypes(): Flow<List<ShiftType>> = flowOf(pattern.cycle.distinctBy { it.id })

    override fun observeOverrides(): Flow<List<DayOverride>> = flowOf(emptyList())

    override suspend fun upsertPattern(pattern: ShiftPattern) = Unit

    override suspend fun setActivePattern(patternId: String?) = Unit

    override suspend fun saveAndActivate(pattern: ShiftPattern) = Unit

    override suspend fun upsertOverride(override: DayOverride) = Unit

    override suspend fun deleteOverride(date: java.time.LocalDate) = Unit
}

private class FakeAlarmRepository : AlarmRepository {
    override fun observeExceptions(): Flow<List<AlarmException>> = flowOf(emptyList())

    override suspend fun upsertException(exception: AlarmException) = Unit

    override suspend fun deleteException(date: java.time.LocalDate) = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    private val current = MutableStateFlow(UserSettings())
    override val settings: Flow<UserSettings> = current

    override suspend fun setSnoozeMinutes(minutes: Int) {
        current.value = current.value.copy(snoozeMinutes = minutes)
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        current.value = current.value.copy(vibrationEnabled = enabled)
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        current.value = current.value.copy(useDynamicColor = enabled)
    }

    override suspend fun setPendingSnooze(epochMillis: Long?, shiftName: String) {
        current.value = current.value.copy(
            pendingSnoozeAtEpochMillis = epochMillis,
            pendingSnoozeShiftName = shiftName,
        )
    }
}
