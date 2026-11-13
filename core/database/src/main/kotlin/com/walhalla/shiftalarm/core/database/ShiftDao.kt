package com.walhalla.shiftalarm.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShiftDao {
    @Transaction
    @Query("SELECT * FROM patterns ORDER BY name")
    abstract fun observePatterns(): Flow<List<PatternWithSlots>>

    @Transaction
    @Query("SELECT * FROM patterns WHERE isActive = 1")
    abstract fun observeActive(): Flow<List<PatternWithSlots>>

    @Query("SELECT * FROM shift_types")
    abstract fun observeShiftTypes(): Flow<List<ShiftTypeEntity>>

    @Query("SELECT * FROM day_overrides")
    abstract fun observeOverrides(): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM alarm_exceptions")
    abstract fun observeAlarmExceptions(): Flow<List<AlarmExceptionEntity>>

    @Query("SELECT isActive FROM patterns WHERE id = :id LIMIT 1")
    abstract suspend fun isActive(id: String): Boolean?

    @Upsert
    abstract suspend fun upsertShiftType(entity: ShiftTypeEntity)

    @Upsert
    abstract suspend fun upsertPattern(entity: PatternEntity)

    @Upsert
    abstract suspend fun upsertOverride(entity: DayOverrideEntity)

    @Upsert
    abstract suspend fun upsertAlarmException(entity: AlarmExceptionEntity)

    @Insert
    abstract suspend fun insertSlot(entity: PatternSlotEntity)

    @Query("DELETE FROM pattern_slots WHERE patternId = :patternId")
    abstract suspend fun deleteSlots(patternId: String)

    @Query("DELETE FROM day_overrides WHERE epochDay = :epochDay")
    abstract suspend fun deleteOverride(epochDay: Long)

    @Query("DELETE FROM alarm_exceptions WHERE epochDay = :epochDay")
    abstract suspend fun deleteAlarmException(epochDay: Long)

    @Query("UPDATE patterns SET isActive = 0")
    abstract suspend fun clearActive()

    @Query("UPDATE patterns SET isActive = 1 WHERE id = :id")
    abstract suspend fun markActive(id: String)

    @Transaction
    open suspend fun savePattern(
        types: List<ShiftTypeEntity>,
        pattern: PatternEntity,
        slots: List<PatternSlotEntity>,
    ) {
        types.forEach { upsertShiftType(it) }
        upsertPattern(pattern)
        deleteSlots(pattern.id)
        slots.forEach { insertSlot(it) }
    }

    @Transaction
    open suspend fun activate(patternId: String?) {
        clearActive()
        if (patternId != null) {
            markActive(patternId)
        }
    }

    @Transaction
    open suspend fun saveAndActivate(
        types: List<ShiftTypeEntity>,
        pattern: PatternEntity,
        slots: List<PatternSlotEntity>,
    ) {
        clearActive()
        types.forEach { upsertShiftType(it) }
        upsertPattern(pattern)
        deleteSlots(pattern.id)
        slots.forEach { insertSlot(it) }
    }
}
