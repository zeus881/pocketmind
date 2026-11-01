package com.manjit.pocketmind.core.llm

/**
 * Performance numbers for one answer — what the "12.4 tok/s · GPU" label shows.
 *
 * @property timeToFirstTokenMs time from sending the prompt to the first text piece
 *   (dominated by "prefill": reading the whole prompt).
 * @property decodeTokensPerSecond tokens generated per second AFTER the first token.
 * @property generatedTokens how many tokens the answer had.
 * @property fromEngine true when the numbers come from LiteRT-LM's own benchmark counters
 *   (exact token counts); false when we estimated them by counting streamed pieces.
 */
data class GenerationStats(
    val timeToFirstTokenMs: Long,
    val decodeTokensPerSecond: Double,
    val generatedTokens: Int,
    val backend: LlmBackend,
    val fromEngine: Boolean,
)
