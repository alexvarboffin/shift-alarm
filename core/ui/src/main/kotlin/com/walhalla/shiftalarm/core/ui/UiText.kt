package com.walhalla.shiftalarm.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * One text type for every screen. A ViewModel stores this instead of a [Context].
 *
 * [Res] is a string resource and optional format args.
 * [Raw] is kept for text that is already a string: validation, a missing type name,
 * or `Throwable.message`. Prefer [Res] for copy that ships in resources.
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

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current)

fun String.asUiText(): UiText = UiText.Raw(this)

fun @receiver:StringRes Int.asUiText(vararg args: Any): UiText = UiText.Res(this, args.toList())
