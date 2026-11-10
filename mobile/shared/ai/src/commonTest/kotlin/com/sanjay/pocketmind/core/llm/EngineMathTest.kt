package com.sanjay.pocketmind.core.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineMathTest {
    @Test
    fun preferGPUTriesGPUThenFallsBackToCPU() {
        assertEquals(listOf(LlmBackend.GPU, LlmBackend.CPU), EngineMath.backendOrder(preferGpu = true))
    }

    @Test
    fun cpuPreferenceNeverTriesGPU() {
        assertEquals(listOf(LlmBackend.CPU), EngineMath.backendOrder(preferGpu = false))
    }

    @Test
    fun aBackendThatCrashedBeforeIsSkipped() {
        assertEquals(listOf(LlmBackend.CPU), EngineMath.backendOrder(preferGpu = true, blocked = setOf(LlmBackend.GPU)))
        assertEquals(
            emptyList<LlmBackend>(),
            EngineMath.backendOrder(preferGpu = false, blocked = setOf(LlmBackend.CPU)),
        )
    }

    @Test
    fun modelFileChecksCatchMissingAndWrongSizeFiles() {
        assertEquals(FailureReason.FILE_NOT_FOUND, EngineMath.modelFileProblem(false, 0, 100))
        assertEquals(FailureReason.FILE_DAMAGED, EngineMath.modelFileProblem(true, 99, 100))
        assertEquals(null, EngineMath.modelFileProblem(true, 100, 100))
        // Unknown expected size: only readability is checked.
        assertEquals(null, EngineMath.modelFileProblem(true, 5, null))
    }

    @Test
    fun decodeRateExcludesTheFirstPieceAndPrefillTime() {
        // 21 pieces; the first at t=1000 ms, the last at t=3000 ms → 20 pieces in 2 s = 10/s.
        assertEquals(10.0, EngineMath.estimateDecodeRate(pieces = 21, firstPieceAtMs = 1000, endAtMs = 3000), 0.001)
    }

    @Test
    fun decodeRateIsZeroWhenThereIsNothingToMeasure() {
        assertEquals(0.0, EngineMath.estimateDecodeRate(pieces = 1, firstPieceAtMs = 0, endAtMs = 500), 0.0)
        assertEquals(0.0, EngineMath.estimateDecodeRate(pieces = 5, firstPieceAtMs = 100, endAtMs = 100), 0.0)
    }
}
