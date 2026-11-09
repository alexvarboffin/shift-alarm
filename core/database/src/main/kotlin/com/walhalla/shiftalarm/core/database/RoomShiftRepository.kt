package com.walhalla.shiftalarm.core.database

import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.AlarmRepository
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftRepository
import com.walhalla.shiftalarm.core.model.ShiftType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomShiftRepository(
    private val dao: ShiftDao,
) : ShiftRepository, AlarmRepository {
    override fun observePatterns(): Flow<List<ShiftPattern>> = combine(
        dao.observePatterns(),
        dao.observeShiftTypes(),
    ) { graphs, types ->
        val byId = types.toDomainMap()
        graphs.map { mapPattern(it, byId) }
    }

    override fun observeActivePattern(): Flow<ShiftPattern?> = combine(
        dao.observeActive(),
        dao.observeShiftTypes(),
    ) { graphs, types ->
        graphs.firstOrNull()?.let { mapPattern(it, types.toDomainMap()) }
    }

    override fun observeShiftTypes(): Flow<List<ShiftType>> =
        dao.observeShiftTypes().map { rows -> rows.map { it.toDomain() } }

    override fun observeOverrides(): Flow<List<DayOverride>> = combine(
        dao.observeOverrides(),
        dao.observeShiftTypes(),
    ) { rows, types ->
        val byId = types.toDomainMap()
        rows.mapNotNull { row ->
            val shift = byId[row.shiftTypeId] ?: return@mapNotNull null
            DayOverride(date = LocalDate.ofEpochDay(row.epochDay), shift = shift)
        }
    }

    override suspend fun upsertPattern(pattern: ShiftPattern) {
        val keepActive = dao.isActive(pattern.id) ?: false
        dao.savePattern(
            types = pattern.cycle.map { it.toEntity() }.distinctBy { it.id },
            pattern = pattern.toEntity(isActive = keepActive),
            slots = pattern.slots(),
        )
    }

    override suspend fun setActivePattern(patternId: String?) {
        dao.activate(patternId)
    }

    override suspend fun saveAndActivate(pattern: ShiftPattern) {
        dao.saveAndActivate(
            types = pattern.cycle.map { it.toEntity() }.distinctBy { it.id },
            pattern = pattern.toEntity(isActive = true),
            slots = pattern.slots(),
        )
    }

    override suspend fun upsertOverride(override: DayOverride) {
        dao.upsertShiftType(override.shift.toEntity())
        dao.upsertOverride(override.toEntity())
    }

    override suspend fun deleteOverride(date: LocalDate) {
        dao.deleteOverride(date.toEpochDay())
    }

    override fun observeExceptions(): Flow<List<AlarmException>> =
        dao.observeAlarmExceptions().map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertException(exception: AlarmException) {
        dao.upsertAlarmException(exception.toEntity())
    }

    override suspend fun deleteException(date: LocalDate) {
        dao.deleteAlarmException(date.toEpochDay())
    }

    private fun List<ShiftTypeEntity>.toDomainMap(): Map<String, ShiftType> =
        associate { it.id to it.toDomain() }
}

private fun ShiftPattern.slots(): List<PatternSlotEntity> = cycle.mapIndexed { index, shift ->
    PatternSlotEntity(
        id = "$id#$index",
        patternId = id,
        position = index,
        shiftTypeId = shift.id,
    )
}
