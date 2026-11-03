package com.walhalla.shiftalarm.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShiftTypeEntity::class,
        PatternEntity::class,
        PatternSlotEntity::class,
        DayOverrideEntity::class,
        AlarmExceptionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ShiftDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
}
