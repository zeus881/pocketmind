package com.sanjay.pocketmind.core.llm

/**
 * Small pure functions used by the platform engines (LiteRtLmEngine on Android; the iOS engine bridge in V3).
 *
 * WHY separate? An engine needs the native library and a real phone, so it can't run
 * in unit tests. Pulling the decision logic out into plain functions lets us test it on the JVM.
 */
object EngineMath {
    private const val MILLIS_PER_SECOND = 1000.0

    /**
     * Which backends to try, in order. GPU is fastest when it works; CPU always works.
     * [blocked] backends crashed the app before (see [LoadGuard]) and are skipped.
     */
    fun backendOrder(preferGpu: Boolean, blocked: Set<LlmBackend> = emptySet()): List<LlmBackend> =
        (if (preferGpu) listOf(LlmBackend.GPU, LlmBackend.CPU) else listOf(LlmBackend.CPU)).filterNot { it in blocked }

    /**
     * Cheap sanity check before handing a file to native code. Returns the problem, or null if
     * the file looks right. [expectedBytes] = null means "size unknown" (only readability is checked).
     * Comparing the exact byte count catches truncated copies and most damage in microseconds;
     * the full SHA-256 check (seconds) runs on demand from the model manager.
     */
    fun modelFileProblem(readable: Boolean, actualBytes: Long, expectedBytes: Long?): FailureReason? = when {
        !readable -> FailureReason.FILE_NOT_FOUND
        expectedBytes != null && actualBytes != expectedBytes -> FailureReason.FILE_DAMAGED
        else -> null
    }

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
