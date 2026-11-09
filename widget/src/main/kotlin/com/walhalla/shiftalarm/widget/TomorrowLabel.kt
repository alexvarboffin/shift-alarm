package com.walhalla.shiftalarm.widget

import com.walhalla.shiftalarm.core.model.DayOverride
import com.walhalla.shiftalarm.core.model.ShiftCalculator
import com.walhalla.shiftalarm.core.model.ShiftPattern
import java.time.LocalDate
import java.util.Locale

internal val CLOCK_CHANGE_ACTIONS: Set<String> = setOf(
    "android.intent.action.TIMEZONE_CHANGED",
    "android.intent.action.TIME_SET",
)

internal fun isClockChangeAction(action: String?): Boolean =
    action != null && action in CLOCK_CHANGE_ACTIONS

internal fun tomorrowLabel(
    pattern: ShiftPattern?,
    overrides: Map<LocalDate, DayOverride>,
    today: LocalDate,
): String {
    if (pattern == null || pattern.cycle.isEmpty()) return "завтра: выходной"
    val resolved = ShiftCalculator.shiftFor(today.plusDays(1), pattern, overrides)
    val name = resolved.shift.name.lowercase(Locale.forLanguageTag("ru"))
    return "завтра: $name"
}
