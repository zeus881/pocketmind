package com.sanjay.pocketmind.core.common.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowExtensionsTest {
    @Test
    fun `throttleLatest drops intermediate values but keeps first and last`() = runTest {
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
        assertTrue("expected few emissions but got ${result.size}", result.size <= 4)
    }

    @Test
    fun `throttleLatest passes through a slow flow unchanged`() = runTest {
        val source = flow {
            repeat(3) {
                emit(it)
                delay(100)
            }
        }

        assertEquals(listOf(0, 1, 2), source.throttleLatest(50).toList())
    }
}
