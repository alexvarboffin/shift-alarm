package com.walhalla.shiftalarm.core.alarm

import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.BuiltinShifts
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectBootSnapshotStoreTest {
    @Test
    fun writeThenReadRoundTripsPatternOverridesAndExceptions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "snapshot-round-trip.preferences_pb")
        val store = DeviceProtectedAlarmStore.create(file)
        val date = LocalDate.of(2026, 6, 2)
        val snapshot = DirectBootSnapshot(
            triggerEpochMillis = 1_780_000_000_000,
            shiftName = "Ночь",
            snooze = false,
            snoozeEpochMillis = null,
            snoozeShiftName = "",
            pattern = ShiftTemplates.twoTwo(LocalDate.of(2026, 6, 1)),
            overrides = listOf(DayOverride(date, BuiltinShifts.night)),
            exceptions = listOf(
                AlarmException(
                    date = date,
                    enabled = true,
                    customTime = LocalDateTime.of(2026, 6, 1, 23, 0),
                ),
            ),
        )

        store.write(snapshot)

        assertEquals(snapshot, store.read())
    }
}
