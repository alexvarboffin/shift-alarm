package com.walhalla.shiftalarm.core.ui

import android.content.Context
import androidx.annotation.StringRes

/**
 * Text a screen can show without the ViewModel holding a Context.
 * [Res] is a string resource; [Raw] is an already resolved message.
 */
sealed interface UiText {
    data class Res(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Raw(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res -> if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args.toTypedArray())
        }
    }
}
