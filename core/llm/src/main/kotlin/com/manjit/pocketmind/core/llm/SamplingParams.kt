package com.manjit.pocketmind.core.llm

/**
 * How the model picks the next token.
 *
 * - [temperature]: 0 = always the most likely token (boring, repetitive); higher = more random
 *   (creative, but more mistakes). 0.7 is a good chat default.
 * - [topK]: only the K most likely tokens are candidates.
 * - [topP]: only the smallest set of tokens whose probabilities add up to P are candidates.
 * - [seed]: makes randomness repeatable; the benchmark uses a fixed seed so runs are comparable.
 */
data class SamplingParams(
    val temperature: Double = DEFAULT_TEMPERATURE,
    val topK: Int = DEFAULT_TOP_K,
    val topP: Double = DEFAULT_TOP_P,
    val seed: Int = DEFAULT_SEED,
) {
    companion object {
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95
        const val DEFAULT_SEED = 0

        /** Translation and summaries should be faithful, not creative. */
        val Precise = SamplingParams(temperature = 0.2, topK = 20)
    }
}
