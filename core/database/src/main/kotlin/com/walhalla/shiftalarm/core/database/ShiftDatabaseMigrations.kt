package com.walhalla.shiftalarm.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ShiftDatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(false)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `shift_types_new` (
                  `id` TEXT NOT NULL,
                  `kind` TEXT NOT NULL,
                  `name` TEXT NOT NULL,
                  `colorArgb` INTEGER NOT NULL,
                  `startHour` INTEGER,
                  `startMinute` INTEGER,
                  `alarmLeadMinutes` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO shift_types_new (id, kind, name, colorArgb, startHour, startMinute, alarmLeadMinutes)
                SELECT id, kind, name, colorArgb, alarmHour, alarmMinute, 90 FROM shift_types
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE shift_types")
            db.execSQL("ALTER TABLE shift_types_new RENAME TO shift_types")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `alarm_exceptions_new` (
                  `epochDay` INTEGER NOT NULL,
                  `enabled` INTEGER NOT NULL,
                  `customEpochDay` INTEGER,
                  `customHour` INTEGER,
                  `customMinute` INTEGER,
                  PRIMARY KEY(`epochDay`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO alarm_exceptions_new (epochDay, enabled, customEpochDay, customHour, customMinute)
                SELECT epochDay, enabled,
                  CASE WHEN hour IS NOT NULL AND minute IS NOT NULL THEN epochDay ELSE NULL END,
                  hour,
                  minute
                FROM alarm_exceptions
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE alarm_exceptions")
            db.execSQL("ALTER TABLE alarm_exceptions_new RENAME TO alarm_exceptions")
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
}
