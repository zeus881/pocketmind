package com.manjit.pocketmind.core.model

import kotlinx.serialization.Serializable

/**
 * One full benchmark run: model load + 3 fixed prompts, on one phone, with one model.
 * These are the real numbers for the README table and the resume — never estimates
 * presented as measurements ([BenchmarkPromptResult.fromEngine] says which is which).
 */
data class BenchmarkResult(
    val id: String,
    val createdAt: Long,
    val modelId: String,
    val modelName: String,
    val backend: String,
    val loadMs: Long,
    val prompts: List<BenchmarkPromptResult>,
    val peakMemoryMb: Int,
    val device: String,
    val androidVersion: String,
) {
    val averageTimeToFirstTokenMs: Long
        get() = if (prompts.isEmpty()) 0 else prompts.sumOf { it.timeToFirstTokenMs } / prompts.size

    val averageDecodeTokensPerSecond: Double
        get() = if (prompts.isEmpty()) 0.0 else prompts.sumOf { it.decodeTokensPerSecond } / prompts.size
}

/** Result for one of the fixed prompts (short / medium / long). */
@Serializable
data class BenchmarkPromptResult(
    val name: String,
    val timeToFirstTokenMs: Long,
    val decodeTokensPerSecond: Double,
    val generatedTokens: Int,
    val totalMs: Long,
    val fromEngine: Boolean,
)
