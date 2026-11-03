package com.walhalla.shiftalarm.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "shift_types")
data class ShiftTypeEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val colorLight: Long,
    val colorDark: Long,
    val startHour: Int?,
    val startMinute: Int?,
    val alarmLeadMinutes: Int,
)

@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    val template: String,
    val startEpochDay: Long,
    val isActive: Boolean,
)

@Entity(
    tableName = "pattern_slots",
    foreignKeys = [
        ForeignKey(
            entity = PatternEntity::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShiftTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patternId", "position"], unique = true),
        Index("shiftTypeId"),
    ],
)
data class PatternSlotEntity(
    @PrimaryKey val id: String,
    val patternId: String,
    val position: Int,
    val shiftTypeId: String,
)

@Entity(
    tableName = "day_overrides",
    foreignKeys = [
        ForeignKey(
            entity = ShiftTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("shiftTypeId")],
)
data class DayOverrideEntity(
    @PrimaryKey val epochDay: Long,
    val shiftTypeId: String,
)

@Entity(tableName = "alarm_exceptions")
data class AlarmExceptionEntity(
    @PrimaryKey val epochDay: Long,
    val enabled: Boolean,
    val customEpochDay: Long?,
    val customHour: Int?,
    val customMinute: Int?,
)

data class PatternWithSlots(
    @Embedded val pattern: PatternEntity,
    @Relation(parentColumn = "id", entityColumn = "patternId")
    val slots: List<PatternSlotEntity>,
)
