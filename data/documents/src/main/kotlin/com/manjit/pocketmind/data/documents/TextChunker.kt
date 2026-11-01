package com.manjit.pocketmind.data.documents

import com.manjit.pocketmind.core.llm.TokenEstimator

/**
 * Splits document text into chunks that fit the model, with OVERLAP between neighbours.
 *
 * WHY chunk? A small on-device model's context is a few thousand tokens; a 30-page PDF is
 * ~15,000. WHY overlap? If a key sentence sits on a chunk boundary, the overlap repeats it at
 * the start of the next chunk, so no idea is cut in half.
 *
 * HOW: text → "units" (sentences, never split mid-sentence unless a single sentence is too
 * long) → greedily packed into chunks of at most [maxTokens]; each new chunk starts with the
 * last units of the previous one, worth about [overlapTokens]. Page numbers are tracked per
 * unit, so every chunk knows its page range — the basis for "[p. 7]" citations in phase 7.
 *
 * Token counts use [TokenEstimator] (≈ 4 chars per token for English).
 */
object TextChunker {
    private data class Unit(val text: String, val page: Int, val tokens: Int)

    private val sentenceEnd = Regex("(?<=[.!?。])\\s+")

    fun chunk(pages: List<PageText>, maxTokens: Int, overlapTokens: Int): List<TextChunk> {
        require(maxTokens > 0) { "maxTokens must be positive" }
        require(overlapTokens in 0 until maxTokens) { "overlap must be smaller than maxTokens" }
        val units = pages.flatMap { page -> toUnits(page, maxTokens) }
        if (units.isEmpty()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        var current = mutableListOf<Unit>()
        var currentTokens = 0
        for (unit in units) {
            if (currentTokens + unit.tokens > maxTokens && current.isNotEmpty()) {
                chunks += build(chunks.size, current)
                current = overlapTail(current, overlapTokens)
                currentTokens = current.sumOf { it.tokens }
            }
            current += unit
            currentTokens += unit.tokens
        }
        chunks += build(chunks.size, current)
        return chunks
    }

    /** Sentences of one page; over-long sentences are cut into word groups. */
    private fun toUnits(page: PageText, maxTokens: Int): List<Unit> = page.text
        .replace(Regex("[ \\t]+"), " ")
        .split(sentenceEnd)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { sentence -> splitLong(sentence, maxTokens) }
        .map { Unit(it, page.page, TokenEstimator.estimate(it) + 1) } // +1 for the joining space

    private fun splitLong(sentence: String, maxTokens: Int): List<String> {
        if (TokenEstimator.estimate(sentence) <= maxTokens) return listOf(sentence)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (word in sentence.split(' ')) {
            if (current.isNotEmpty() && TokenEstimator.estimate("$current $word") > maxTokens) {
                parts += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    /** The last units of a finished chunk, about [overlapTokens] long, to start the next one. */
    private fun overlapTail(units: List<Unit>, overlapTokens: Int): MutableList<Unit> {
        val tail = ArrayDeque<Unit>()
        var tokens = 0
        for (unit in units.asReversed()) {
            if (tokens + unit.tokens > overlapTokens) break
            tail.addFirst(unit)
            tokens += unit.tokens
        }
        return tail.toMutableList()
    }

    private fun build(index: Int, units: List<Unit>) = TextChunk(
        index = index,
        text = units.joinToString(" ") { it.text },
        startPage = units.first().page,
        endPage = units.last().page,
    )
}
