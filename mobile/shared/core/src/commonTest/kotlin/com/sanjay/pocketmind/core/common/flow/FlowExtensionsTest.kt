package com.sanjay.pocketmind.core.common.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class FlowExtensionsTest {
    @Test
    fun throttleLatestDropsIntermediateValuesButKeepsFirstAndLast() = runTest {
        // 100 values, one every 1 ms of virtual time (like a very fast LLM).
        val source = flow {
            repeat(100) {
                emit(it)
                delay(1)
            }
        }

        val result = source.throttleLatest(50).toList()

        assertEquals(0, result.first())
        assertEquals(99, result.last())
        // ~100 ms of input with a 50 ms window should produce only a handful of emissions.
        assertTrue(result.size <= 4, "expected few emissions but got ${result.size}")
    }

    @Test
    fun throttleLatestPassesThroughASlowFlowUnchanged() = runTest {
        val source = flow {
            repeat(3) {
                emit(it)
                delay(100)
            }
        }

        assertEquals(listOf(0, 1, 2), source.throttleLatest(50).toList())
    }
}
