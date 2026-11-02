package com.sanjay.pocketmind.core.llm

/**
 * Small pure functions used by [LiteRtLmEngine].
 *
 * WHY separate? The engine itself needs the native library and a real phone, so it can't run
 * in unit tests. Pulling the decision logic out into plain functions lets us test it on the JVM.
 */
internal object EngineMath {
    private const val MILLIS_PER_SECOND = 1000.0

    /** Which backends to try, in order. GPU is fastest when it works; CPU always works. */
    fun backendOrder(preferGpu: Boolean): List<LlmBackend> =
        if (preferGpu) listOf(LlmBackend.GPU, LlmBackend.CPU) else listOf(LlmBackend.CPU)

    /**
     * Decode speed from our own measurements: pieces streamed after the first one, divided by
     * the time since the first one. Prefill time (before the first piece) is excluded on purpose,
     * because it depends on prompt length, not on generation speed.
     */
    fun estimateDecodeRate(pieces: Int, firstPieceAtMs: Long, endAtMs: Long): Double {
        val decodePieces = pieces - 1
        val elapsedMs = endAtMs - firstPieceAtMs
        if (decodePieces <= 0 || elapsedMs <= 0) return 0.0
        return decodePieces * MILLIS_PER_SECOND / elapsedMs
    }
}
