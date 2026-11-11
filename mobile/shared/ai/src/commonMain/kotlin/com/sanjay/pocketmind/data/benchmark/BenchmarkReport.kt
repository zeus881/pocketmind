package com.sanjay.pocketmind.data.benchmark

import com.sanjay.pocketmind.core.common.format.formatFixed
import com.sanjay.pocketmind.core.common.format.twoDigits
import com.sanjay.pocketmind.core.model.BenchmarkResult
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats a run as plain text for the share sheet (GitHub issues, README tables, chats).
 *
 * Numbers always use "." on purpose (formatFixed, never the phone's locale): a shared benchmark must read
 * the same everywhere ("52.1", not "52,1"). Estimated values are marked with `*` — we never present an estimate
 * as a measurement.
 */
object BenchmarkReport {
    private const val MS_PER_SECOND = 1000.0

    fun format(result: BenchmarkResult, zone: TimeZone = TimeZone.currentSystemDefault()): String = buildString {
        val time = Instant.fromEpochMilliseconds(result.createdAt).toLocalDateTime(zone)
        val date = "${time.date} ${time.hour.twoDigits()}:${time.minute.twoDigits()}"
        appendLine("PocketMind benchmark — $date")
        appendLine("Device: ${result.device} · ${result.androidVersion}")
        appendLine("Model: ${result.modelName} · Backend: ${result.backend}")
        appendLine("Model load: ${seconds(result.loadMs)} · Peak memory: ${result.peakMemoryMb} MB")
        appendLine()
        appendLine(row("Prompt", "1st token", "Decode", "Tokens"))
        result.prompts.forEach { p ->
            val mark = if (p.fromEngine) "" else "*"
            appendLine(
                row(
                    p.name,
                    seconds(p.timeToFirstTokenMs),
                    "${p.decodeTokensPerSecond.formatFixed(1)} tok/s$mark",
                    p.generatedTokens.toString(),
                ),
            )
        }
        appendLine()
        append(
            "Average: first token ${seconds(result.averageTimeToFirstTokenMs)} · " +
                "${result.averageDecodeTokensPerSecond.formatFixed(1)} tok/s",
        )
        if (result.prompts.any { !it.fromEngine }) append("\n* estimated from streamed pieces")
    }

    private fun seconds(ms: Long) = "${(ms / MS_PER_SECOND).formatFixed(2)} s"

    /** One table row, same widths as v2's "%-8s %10s %14s %8s": name left, numbers right. */
    private fun row(name: String, firstToken: String, decode: String, tokens: String) =
        "${name.padEnd(NAME_WIDTH)} ${firstToken.padStart(FIRST_TOKEN_WIDTH)} " +
            "${decode.padStart(DECODE_WIDTH)} ${tokens.padStart(TOKENS_WIDTH)}"

    private const val NAME_WIDTH = 8
    private const val FIRST_TOKEN_WIDTH = 10
    private const val DECODE_WIDTH = 14
    private const val TOKENS_WIDTH = 8
}
