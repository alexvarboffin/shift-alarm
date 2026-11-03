package com.walhalla.shiftalarm.core.model

/** Slot count for a custom cycle. Preset templates are not checked against this. */
object PatternLimits {
    const val MIN_SLOTS: Int = 1
    const val MAX_SLOTS: Int = 31

    fun slotCountMessage(count: Int): String? = when {
        count < MIN_SLOTS -> "В цикле должен быть хотя бы один слот."
        count > MAX_SLOTS -> "В цикле не больше 31 слота."
        else -> null
    }
}
