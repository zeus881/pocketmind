package com.sanjay.pocketmind.data.benchmark

import com.sanjay.pocketmind.core.model.BenchmarkResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats a run as plain text for the share sheet (GitHub issues, README tables, chats).
 *
 * Numbers use Locale.US on purpose: a shared benchmark must read the same everywhere
 * ("52.1", not "52,1"). Estimated values are marked with `*` — we never present an estimate
 * as a measurement.
 */
object BenchmarkReport {
    private const val MS_PER_SECOND = 1000.0

    fun format(result: BenchmarkResult, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
            .format(Instant.ofEpochMilli(result.createdAt).atZone(zone))
        appendLine("PocketMind benchmark — $date")
        appendLine("Device: ${result.device} · ${result.androidVersion}")
        appendLine("Model: ${result.modelName} · Backend: ${result.backend}")
        appendLine("Model load: ${seconds(result.loadMs)} · Peak memory: ${result.peakMemoryMb} MB")
        appendLine()
        appendLine(String.format(Locale.US, "%-8s %10s %14s %8s", "Prompt", "1st token", "Decode", "Tokens"))
        result.prompts.forEach { p ->
            val mark = if (p.fromEngine) "" else "*"
            appendLine(
                String.format(
                    Locale.US,
                    "%-8s %10s %14s %8d",
                    p.name,
                    seconds(p.timeToFirstTokenMs),
                    String.format(Locale.US, "%.1f tok/s%s", p.decodeTokensPerSecond, mark),
                    p.generatedTokens,
                ),
            )
        }
        appendLine()
        append(
            String.format(
                Locale.US,
                "Average: first token %s · %.1f tok/s",
                seconds(result.averageTimeToFirstTokenMs),
                result.averageDecodeTokensPerSecond,
            ),
        )
        if (result.prompts.any { !it.fromEngine }) append("\n* estimated from streamed pieces")
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.2f s", ms / MS_PER_SECOND)
}
