package com.walhalla.shiftalarm.core.model

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Patterns, the one active pattern, and per-date shift overrides.
 * Implementations live in `:core:database`. Feature modules depend on this interface only.
 */
interface ShiftRepository {
    fun observePatterns(): Flow<List<ShiftPattern>>

    fun observeActivePattern(): Flow<ShiftPattern?>

    fun observeShiftTypes(): Flow<List<ShiftType>>

    fun observeOverrides(): Flow<List<DayOverride>>

    suspend fun upsertPattern(pattern: ShiftPattern)

    suspend fun setActivePattern(patternId: String?)

    /** Writes the cycle and makes it the only active pattern in one transaction. */
    suspend fun saveAndActivate(pattern: ShiftPattern)

    suspend fun upsertOverride(override: DayOverride)

    suspend fun deleteOverride(date: LocalDate)
}

/** Per-date alarm exceptions. Stored in Room next to patterns, not in DataStore. */
interface AlarmRepository {
    fun observeExceptions(): Flow<List<AlarmException>>

    suspend fun upsertException(exception: AlarmException)

    suspend fun deleteException(date: LocalDate)
}

/**
 * Scalar preferences and the single in-flight snooze marker.
 * Implementation lives in `:core:datastore`.
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setSnoozeMinutes(minutes: Int)

    suspend fun setVibrationEnabled(enabled: Boolean)

    suspend fun setUseDynamicColor(enabled: Boolean)

    suspend fun setPendingSnooze(epochMillis: Long?, shiftName: String)
}

/** Asks the home-screen widget to redraw. Implementation lives in `:widget`. */
interface HomeWidgetRefresher {
    suspend fun refresh()
}
