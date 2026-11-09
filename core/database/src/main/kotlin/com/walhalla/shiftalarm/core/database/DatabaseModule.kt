package com.walhalla.shiftalarm.core.database

import androidx.room.Room
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.ShiftRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), ShiftDatabase::class.java, "shift-alarm.db")
            .addMigrations(
                ShiftDatabaseMigrations.MIGRATION_1_2,
                ShiftDatabaseMigrations.MIGRATION_2_3,
            )
            .build()
    }
    single { get<ShiftDatabase>().shiftDao() }
    single { RoomShiftRepository(get()) } binds arrayOf(ShiftRepository::class, AlarmRepository::class)
}
