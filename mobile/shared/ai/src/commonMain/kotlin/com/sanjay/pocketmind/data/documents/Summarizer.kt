package com.sanjay.pocketmind.data.documents

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.TokenEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What the documents screen shows while summarizing. */
sealed interface SummaryProgress {
    data object Preparing : SummaryProgress

    /** MAP step: "Summarizing part X of Y". */
    data class Mapping(val part: Int, val total: Int) : SummaryProgress

    /** REDUCE step: combining partial summaries (round 1, 2, ...). */
    data class Reducing(val round: Int) : SummaryProgress

    data class Done(val summary: String, val parts: Int) : SummaryProgress

    data class Failed(val kind: ErrorKind) : SummaryProgress
}

/**
 * Summarizes documents of any length with MAP-REDUCE:
 * ```
 *  30 pages ──chunk (≈1,500 tokens, 10% overlap)──▶ [c1][c2]…[c12]
 *  MAP:    each chunk → 3–5 bullets           (12 small LLM calls, fresh session each)
 *  REDUCE: join bullets; still too long? → combine groups into shorter lists, repeat
 *  FINAL:  one overview + 5–8 bullets
 * ```
 * WHY: the whole PDF doesn't fit in the model's context window, and a small model summarizes a
 * short text far better than a long one. Cost: one LLM call per chunk (progress is shown, and
 * collection can be cancelled at any point — cancelling the Flow stops the current generation).
 */
class Summarizer(private val task: LlmTask) {

    fun summarize(pages: List<PageText>): Flow<SummaryProgress> = flow {
        emit(SummaryProgress.Preparing)
        val loaded = task.ensureLoaded()
        if (loaded is AppResult.Failure) {
            emit(SummaryProgress.Failed(loaded.kind))
            return@flow
        }
        val budget = chunkBudget(task.contextWindowTokens)
        val chunks = TextChunker.chunk(pages, budget, overlapTokens = budget / OVERLAP_DIVISOR)
        if (chunks.isEmpty()) {
            emit(SummaryProgress.Failed(ErrorKind.NO_TEXT_IN_DOCUMENT))
            return@flow
        }
        if (chunks.size == 1) {
            emit(SummaryProgress.Mapping(1, 1))
            emit(SummaryProgress.Done(ask(PromptTemplates.singlePassPrompt(chunks[0].text)), parts = 1))
            return@flow
        }

        // MAP
        var summaries = chunks.mapIndexed { i, chunk ->
            emit(SummaryProgress.Mapping(i + 1, chunks.size))
            ask(PromptTemplates.mapPrompt(chunk.text, i + 1, chunks.size))
        }

        // REDUCE until everything fits into one final prompt.
        var round = 1
        while (tooLong(summaries, budget) && round <= MAX_REDUCE_ROUNDS) {
            emit(SummaryProgress.Reducing(round++))
            summaries = groupByBudget(summaries, budget).map { group ->
                ask(PromptTemplates.reducePrompt(group, isFinal = false))
            }
        }
        emit(SummaryProgress.Reducing(round))
        val final = ask(PromptTemplates.reducePrompt(summaries, isFinal = true))
        emit(SummaryProgress.Done(final, parts = chunks.size))
    }

    /** One LLM call in a fresh session. */
    private suspend fun ask(prompt: String): String = task.complete(PromptTemplates.SUMMARY_SYSTEM, prompt)

    private fun tooLong(summaries: List<String>, budget: Int) =
        TokenEstimator.estimate(summaries.joinToString("\n\n")) > budget

    companion object {
        private const val MAX_CHUNK_TOKENS = 1_500
        private const val ANSWER_RESERVE_TOKENS = 1_024
        private const val PROMPT_OVERHEAD_TOKENS = 200
        private const val OVERLAP_DIVISOR = 10 // 10% overlap
        private const val MAX_REDUCE_ROUNDS = 5

        /** ~1,500 tokens, but never more than the context window minus room for the answer. */
        fun chunkBudget(contextWindow: Int): Int {
            val available = contextWindow - ANSWER_RESERVE_TOKENS - PROMPT_OVERHEAD_TOKENS
            return minOf(MAX_CHUNK_TOKENS, available).coerceAtLeast(1)
        }

        /** Packs consecutive summaries into groups that each fit [budget] (at least one per group). */
        fun groupByBudget(items: List<String>, budget: Int): List<List<String>> {
            val groups = mutableListOf<MutableList<String>>()
            var used = 0
            for (item in items) {
                val cost = TokenEstimator.estimate(item)
                if (groups.isEmpty() || (used + cost > budget && groups.last().isNotEmpty())) {
                    groups += mutableListOf<String>()
                    used = 0
                }
                groups.last() += item
                used += cost
            }
            return groups
        }
    }
}
