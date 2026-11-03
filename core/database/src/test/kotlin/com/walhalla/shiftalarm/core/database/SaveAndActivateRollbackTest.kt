package com.walhalla.shiftalarm.core.database

import android.database.sqlite.SQLiteConstraintException
import android.os.Looper
import androidx.room.Room
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftTemplates
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SaveAndActivateRollbackTest {
    @Test
    fun failedSaveKeepsThePreviousActivePattern() {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, ShiftDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.shiftDao()
            val repository = RoomShiftRepository(dao)
            val start = LocalDate.of(2026, 1, 5)
            runBlocking { repository.saveAndActivate(ShiftTemplates.twoTwo(start).copy(id = "original", name = "Был")) }

            try {
                runBlocking {
                    dao.saveAndActivate(
                        types = emptyList(),
                        pattern = PatternEntity(
                            id = "broken",
                            name = "Сломан",
                            template = PatternTemplate.CUSTOM.name,
                            startEpochDay = start.toEpochDay(),
                            isActive = true,
                        ),
                        slots = listOf(
                            PatternSlotEntity(
                                id = "broken#0",
                                patternId = "broken",
                                position = 0,
                                shiftTypeId = "missing-type",
                            ),
                        ),
                    )
                }
                fail("saveAndActivate should reject a slot whose shift type is missing")
            } catch (error: Exception) {
                assertTrue(error.isConstraintFailure())
            }

            val active = awaitFirst(repository.observeActivePattern())
            assertEquals("original", active?.id)
            assertEquals("Был", active?.name)
            assertTrue(awaitFirst(repository.observePatterns()).none { it.id == "broken" })
        } finally {
            database.close()
        }
    }

    private fun <T> awaitFirst(flow: Flow<T>): T {
        val value = AtomicReference<T>()
        val done = CountDownLatch(1)
        val job = CoroutineScope(Dispatchers.IO).launch {
            value.set(flow.first())
            done.countDown()
        }
        val started = System.nanoTime()
        while (!done.await(50, TimeUnit.MILLISECONDS)) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(5)) {
                job.cancel()
                fail("Room flow did not emit")
            }
        }
        return value.get()
    }

    private fun Throwable.isConstraintFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLiteConstraintException) return true
            current = current.cause
        }
        return false
    }
}
