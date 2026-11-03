package com.walhalla.shiftalarm.core.database

import android.content.Context
import androidx.room.Room
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.ShiftRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShiftDatabase =
        Room.databaseBuilder(context, ShiftDatabase::class.java, "shift-alarm.db")
            .addMigrations(ShiftDatabaseMigrations.MIGRATION_1_2)
            .build()

    @Provides
    fun provideShiftDao(database: ShiftDatabase): ShiftDao = database.shiftDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds
    abstract fun bindShiftRepository(impl: RoomShiftRepository): ShiftRepository

    @Binds
    abstract fun bindAlarmRepository(impl: RoomShiftRepository): AlarmRepository
}
