package com.walhalla.shiftalarm.core.alarm

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import com.walhalla.shiftalarm.core.model.AlarmException
import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.PatternTemplate
import com.walhalla.shiftalarm.core.model.ShiftKind
import com.walhalla.shiftalarm.core.model.ShiftPattern
import com.walhalla.shiftalarm.core.model.ShiftType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * What [AlarmManagerAlarmScheduler.scheduleNext] needs before the first unlock.
 * Lives in device-protected storage, not the credential-encrypted Room database.
 */
data class DirectBootSnapshot(
    val triggerEpochMillis: Long?,
    val shiftName: String,
    val snooze: Boolean,
    val snoozeEpochMillis: Long?,
    val snoozeShiftName: String,
    val pattern: ShiftPattern?,
    val overrides: List<DayOverride>,
    val exceptions: List<AlarmException>,
)

@Singleton
class DeviceProtectedAlarmStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val snapshotKey = stringPreferencesKey("snapshot_json")
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = {
            File(context.createDeviceProtectedStorageContext().filesDir, "shift_alarm_direct_boot.preferences_pb")
        },
    )

    suspend fun read(): DirectBootSnapshot? {
        val raw = dataStore.data.first()[snapshotKey] ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    suspend fun write(snapshot: DirectBootSnapshot) {
        val encoded = encode(snapshot)
        dataStore.edit { prefs ->
            prefs[snapshotKey] = encoded
        }
    }
}

private fun encode(snapshot: DirectBootSnapshot): String {
    val json = JSONObject()
    json.put("triggerEpochMillis", snapshot.triggerEpochMillis ?: JSONObject.NULL)
    json.put("shiftName", snapshot.shiftName)
    json.put("snooze", snapshot.snooze)
    json.put("snoozeEpochMillis", snapshot.snoozeEpochMillis ?: JSONObject.NULL)
    json.put("snoozeShiftName", snapshot.snoozeShiftName)
    json.put("pattern", snapshot.pattern?.let(::encodePattern) ?: JSONObject.NULL)
    json.put("overrides", JSONArray().apply {
        snapshot.overrides.forEach { put(encodeOverride(it)) }
    })
    json.put("exceptions", JSONArray().apply {
        snapshot.exceptions.forEach { put(encodeException(it)) }
    })
    return json.toString()
}

private fun decode(raw: String): DirectBootSnapshot {
    val json = JSONObject(raw)
    return DirectBootSnapshot(
        triggerEpochMillis = json.optLongOrNull("triggerEpochMillis"),
        shiftName = json.optString("shiftName"),
        snooze = json.optBoolean("snooze"),
        snoozeEpochMillis = json.optLongOrNull("snoozeEpochMillis"),
        snoozeShiftName = json.optString("snoozeShiftName"),
        pattern = if (json.isNull("pattern")) null else decodePattern(json.getJSONObject("pattern")),
        overrides = json.optJSONArray("overrides").toList { decodeOverride(it) },
        exceptions = json.optJSONArray("exceptions").toList { decodeException(it) },
    )
}

private fun encodePattern(pattern: ShiftPattern): JSONObject = JSONObject()
    .put("id", pattern.id)
    .put("name", pattern.name)
    .put("template", pattern.template.name)
    .put("startEpochDay", pattern.startDate.toEpochDay())
    .put("cycle", JSONArray().apply { pattern.cycle.forEach { put(encodeShift(it)) } })

private fun decodePattern(json: JSONObject): ShiftPattern = ShiftPattern(
    id = json.getString("id"),
    name = json.getString("name"),
    template = PatternTemplate.valueOf(json.getString("template")),
    startDate = LocalDate.ofEpochDay(json.getLong("startEpochDay")),
    cycle = json.getJSONArray("cycle").toList { decodeShift(it) },
)

private fun encodeShift(shift: ShiftType): JSONObject = JSONObject()
    .put("id", shift.id)
    .put("kind", shift.kind.name)
    .put("name", shift.name)
    .put("colorArgb", shift.colorArgb)
    .put("hour", shift.alarmTime?.hour ?: JSONObject.NULL)
    .put("minute", shift.alarmTime?.minute ?: JSONObject.NULL)

private fun decodeShift(json: JSONObject): ShiftType {
    val hour = json.optIntOrNull("hour")
    val minute = json.optIntOrNull("minute")
    return ShiftType(
        id = json.getString("id"),
        kind = ShiftKind.valueOf(json.getString("kind")),
        name = json.getString("name"),
        colorArgb = json.getInt("colorArgb"),
        alarmTime = if (hour != null && minute != null) LocalTime.of(hour, minute) else null,
    )
}

private fun encodeOverride(override: DayOverride): JSONObject = JSONObject()
    .put("epochDay", override.date.toEpochDay())
    .put("shift", encodeShift(override.shift))

private fun decodeOverride(json: JSONObject): DayOverride = DayOverride(
    date = LocalDate.ofEpochDay(json.getLong("epochDay")),
    shift = decodeShift(json.getJSONObject("shift")),
)

private fun encodeException(exception: AlarmException): JSONObject = JSONObject()
    .put("epochDay", exception.date.toEpochDay())
    .put("enabled", exception.enabled)
    .put("hour", exception.time?.hour ?: JSONObject.NULL)
    .put("minute", exception.time?.minute ?: JSONObject.NULL)

private fun decodeException(json: JSONObject): AlarmException {
    val hour = json.optIntOrNull("hour")
    val minute = json.optIntOrNull("minute")
    return AlarmException(
        date = LocalDate.ofEpochDay(json.getLong("epochDay")),
        enabled = json.getBoolean("enabled"),
        time = if (hour != null && minute != null) LocalTime.of(hour, minute) else null,
    )
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun <T> JSONArray?.toList(decode: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(decode(getJSONObject(index)))
        }
    }
}
