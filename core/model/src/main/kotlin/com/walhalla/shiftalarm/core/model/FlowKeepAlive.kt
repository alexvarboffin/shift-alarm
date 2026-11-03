package com.walhalla.shiftalarm.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * An upstream failure emits [fallback] and leaves the flow open, so a [kotlinx.coroutines.flow.combine]
 * downstream keeps receiving the other sources. The error is not the exception message.
 */
fun <T> Flow<T>.keepAlive(fallback: T, onError: () -> Unit): Flow<T> = flow {
    try {
        collect { emit(it) }
        awaitCancellation()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        onError()
        emit(fallback)
        awaitCancellation()
    }
}
