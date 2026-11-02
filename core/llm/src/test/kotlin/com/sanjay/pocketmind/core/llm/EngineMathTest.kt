package com.sanjay.pocketmind.core.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineMathTest {
    @Test
    fun `prefer GPU tries GPU then falls back to CPU`() {
        assertEquals(listOf(LlmBackend.GPU, LlmBackend.CPU), EngineMath.backendOrder(preferGpu = true))
    }

    @Test
    fun `CPU preference never tries GPU`() {
        assertEquals(listOf(LlmBackend.CPU), EngineMath.backendOrder(preferGpu = false))
    }

    @Test
    fun `decode rate excludes the first piece and prefill time`() {
        // 21 pieces; the first at t=1000 ms, the last at t=3000 ms → 20 pieces in 2 s = 10/s.
        assertEquals(10.0, EngineMath.estimateDecodeRate(pieces = 21, firstPieceAtMs = 1000, endAtMs = 3000), 0.001)
    }

    @Test
    fun `decode rate is zero when there is nothing to measure`() {
        assertEquals(0.0, EngineMath.estimateDecodeRate(pieces = 1, firstPieceAtMs = 0, endAtMs = 500), 0.0)
        assertEquals(0.0, EngineMath.estimateDecodeRate(pieces = 5, firstPieceAtMs = 100, endAtMs = 100), 0.0)
    }
}
