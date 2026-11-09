package com.walhalla.shiftalarm.core.alarm

import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.AppPermission
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftType
import com.walhalla.shiftalarm.core.model.UserSettings
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmRescheduleWatcherTest {
    @Test
    fun overrideChangeSchedulesTheNextAlarmAgain() {
        val shifts = WatchShiftRepository()
        val scheduler = CountingScheduler()
        val watcher = AlarmRescheduleWatcher(
            shifts,
            WatchAlarmRepository(),
            WatchSettingsRepository(),
            scheduler,
            Clock.systemUTC(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        watcher.start()
        assertEquals(1, scheduler.count)

        shifts.overrides.value = listOf(DayOverride(LocalDate.of(2026, 4, 2), BuiltinShifts.night))

        assertEquals(2, scheduler.count)
    }
}

private class CountingScheduler : AlarmScheduler {
    var count: Int = 0

    override suspend fun scheduleNext(nowEpochMillis: Long) {
        count += 1
    }

    override suspend fun cancelNext() = Unit

    override suspend fun snooze(minutes: Int, shiftName: String) = Unit

    override suspend fun dismiss() = Unit

    override fun canScheduleExactAlarms(): Boolean = true

    override fun canUseFullScreenIntent(): Boolean = true
}

private class WatchShiftRepository : ShiftRepository {
    val overrides = MutableStateFlow<List<DayOverride>>(emptyList())

    override fun observePatterns(): Flow<List<ShiftPattern>> = MutableStateFlow(emptyList())

    override fun observeActivePattern(): Flow<ShiftPattern?> = MutableStateFlow(null)

    override fun observeShiftTypes(): Flow<List<ShiftType>> = MutableStateFlow(emptyList())

    override fun observeOverrides(): Flow<List<DayOverride>> = overrides

    override suspend fun upsertPattern(pattern: ShiftPattern) = Unit

    override suspend fun setActivePattern(patternId: String?) = Unit

    override suspend fun saveAndActivate(pattern: ShiftPattern) = Unit

    override suspend fun upsertOverride(override: DayOverride) = Unit

    override suspend fun deleteOverride(date: LocalDate) = Unit
}

private class WatchAlarmRepository : AlarmRepository {
    override fun observeExceptions(): Flow<List<AlarmException>> = MutableStateFlow(emptyList())

    override suspend fun upsertException(exception: AlarmException) = Unit

    override suspend fun deleteException(date: LocalDate) = Unit
}

private class WatchSettingsRepository : SettingsRepository {
    override val requestedPermissions: Flow<Set<AppPermission>> = MutableStateFlow(emptySet())

    override suspend fun markRequested(permission: AppPermission) = Unit

    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())

    override suspend fun setSnoozeMinutes(minutes: Int) = Unit

    override suspend fun setVibrationEnabled(enabled: Boolean) = Unit

    override suspend fun setUseDynamicColor(enabled: Boolean) = Unit

    override suspend fun setPendingSnooze(epochMillis: Long?, shiftName: String) = Unit
}
