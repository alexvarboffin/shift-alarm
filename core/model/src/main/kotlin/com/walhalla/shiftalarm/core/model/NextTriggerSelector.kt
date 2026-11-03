package com.walhalla.shiftalarm.core.model

import java.time.ZoneId

data class ScheduledTrigger(
    val epochMillis: Long,
    val shiftName: String,
    val snooze: Boolean,
)

/**
 * Picks the single alarm the process is allowed to have armed.
 * A snooze strictly after [notBeforeEpochMillis] wins over the next shift alarm.
 */
object NextTriggerSelector {
    fun select(
        notBeforeEpochMillis: Long,
        snoozeEpochMillis: Long?,
        snoozeShiftName: String,
        planned: PlannedAlarm?,
        zone: ZoneId,
    ): ScheduledTrigger? {
        if (snoozeEpochMillis != null && snoozeEpochMillis > notBeforeEpochMillis) {
            return ScheduledTrigger(
                epochMillis = snoozeEpochMillis,
                shiftName = snoozeShiftName,
                snooze = true,
            )
        }
        val alarm = planned ?: return null
        val epoch = alarm.triggerAt.atZone(zone).toInstant().toEpochMilli()
        if (epoch <= notBeforeEpochMillis) return null
        return ScheduledTrigger(
            epochMillis = epoch,
            shiftName = alarm.shift.name,
            snooze = false,
        )
    }
}
