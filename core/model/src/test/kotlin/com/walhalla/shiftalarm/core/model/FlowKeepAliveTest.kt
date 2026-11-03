package com.walhalla.shiftalarm.core.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowKeepAliveTest {
    @Test
    fun siblingFlowStillUpdatesAfterOneSourceFails() {
        val live = MutableStateFlow("first")
        val broken = flow<String> { throw IllegalStateException("disk") }
        val seen = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val job = scope.launch {
            combine(
                live.keepAlive("fallback") { },
                broken.keepAlive("fallback") { },
            ) { left, right -> "$left|$right" }
                .collect { seen += it }
        }
        live.value = "second"
        job.cancel()
        scope.cancel()
        assertEquals(listOf("first|fallback", "second|fallback"), seen)
    }
}
