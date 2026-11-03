package com.walhalla.shiftalarm.core.model

data class ScheduledTrigger(
    val epochMillis: Long,
    val shiftName: String,
    val snooze: Boolean,
)

/**
 * Picks the single alarm the process is allowed to have armed.
 * A snooze strictly after [nowEpochMillis] wins over the next shift alarm.
 */
object NextTriggerSelector {
    fun select(
        nowEpochMillis: Long,
        snoozeEpochMillis: Long?,
        snoozeShiftName: String,
        planned: PlannedAlarm?,
    ): ScheduledTrigger? {
        if (snoozeEpochMillis != null && snoozeEpochMillis > nowEpochMillis) {
            return ScheduledTrigger(
                epochMillis = snoozeEpochMillis,
                shiftName = snoozeShiftName,
                snooze = true,
            )
        }
        val alarm = planned ?: return null
        val epoch = alarm.triggerAt.toInstant().toEpochMilli()
        if (epoch <= nowEpochMillis) return null
        return ScheduledTrigger(
            epochMillis = epoch,
            shiftName = alarm.shift.name,
            snooze = false,
        )
    }
}
