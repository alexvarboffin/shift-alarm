package com.walhalla.shiftalarm.core.alarm

import android.util.Log
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.SettingsRepository
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.UserSettings
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single owner of rescheduling after a write.
 * Pattern saves, day overrides, alarm exceptions, and settings all flow through here.
 * [AlarmScheduler.scheduleNext] recomputes the next trigger and rewrites the
 * device-protected snapshot. Callers do not schedule on their own.
 */
class AlarmRescheduleWatcher(
    private val shiftRepository: ShiftRepository,
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val kick = MutableStateFlow(0)
        scope.launch {
            var handled = 0
            kick.collect { generation ->
                if (generation == handled) return@collect
                handled = generation
                try {
                    scheduler.scheduleNext(clock.millis())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "scheduleNext after data change failed", error)
                }
            }
        }
        scope.launch {
            combine(
                shiftRepository.observePatterns(),
                shiftRepository.observeActivePattern(),
                shiftRepository.observeOverrides(),
                alarmRepository.observeExceptions(),
                settingsRepository.settings,
            ) { patterns, active, overrides, exceptions, settings ->
                WatchSnapshot(patterns, active, overrides, exceptions, settings)
            }
                .distinctUntilChanged()
                .collect { kick.update { generation -> generation + 1 } }
        }
    }

    private data class WatchSnapshot(
        val patterns: List<ShiftPattern>,
        val active: ShiftPattern?,
        val overrides: List<DayOverride>,
        val exceptions: List<AlarmException>,
        val settings: UserSettings,
    )

    private companion object {
        const val TAG: String = "ShiftAlarm"
    }
}
