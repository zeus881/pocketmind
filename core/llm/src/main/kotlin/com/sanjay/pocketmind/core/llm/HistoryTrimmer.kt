package com.sanjay.pocketmind.core.llm

import com.sanjay.pocketmind.core.model.ChatTurn
import com.sanjay.pocketmind.core.model.TurnRole

/**
 * Fits a long chat history into the model's limited context window.
 *
 * PROBLEM: a small on-device model holds only a few thousand tokens. A long chat would
 * overflow it and fail. SOLUTION:
 * 1. Keep the NEWEST turns that fit in [budgetTokens] (recent context matters most).
 * 2. Compress the dropped OLDER turns into one short note ("Earlier in this chat: …")
 *    that is added to the system prompt, so the model still knows the gist.
 *
 * The note is *extractive* (first sentence of each dropped turn) rather than written by the
 * LLM. That costs zero extra inference time and is deterministic — a deliberate trade-off;
 * an LLM-written summary would be higher quality but adds seconds of latency per message.
 */
object HistoryTrimmer {
    private const val MAX_SNIPPET_CHARS = 120
    private const val NOTE_SHARE_OF_BUDGET = 5 // the note may use at most 1/5 of the budget

    data class Result(val turns: List<ChatTurn>, val summaryNote: String?)

    fun trim(history: List<ChatTurn>, budgetTokens: Int): Result {
        if (budgetTokens <= 0) return Result(emptyList(), null)

        // Walk from newest to oldest, keeping turns while they fit.
        var used = 0
        var firstKept = history.size
        for (i in history.indices.reversed()) {
            val cost = TokenEstimator.estimate(history[i].text)
            if (used + cost > budgetTokens) break
            used += cost
            firstKept = i
        }
        var kept = history.subList(firstKept, history.size)
        // A conversation must not start with an orphan assistant reply.
        if (kept.firstOrNull()?.role == TurnRole.ASSISTANT) kept = kept.drop(1)
        val dropped = history.subList(0, history.size - kept.size)

        return Result(kept, buildNote(dropped, budgetTokens / NOTE_SHARE_OF_BUDGET))
    }

    private fun buildNote(dropped: List<ChatTurn>, noteBudget: Int): String? {
        if (dropped.isEmpty() || noteBudget <= 0) return null
        val note = StringBuilder("Earlier in this conversation:")
        for (turn in dropped.asReversed()) { // newest dropped first: most relevant
            val who = if (turn.role == TurnRole.USER) "User" else "Assistant"
            val line = "\n- $who: ${firstSentence(turn.text)}"
            if (TokenEstimator.estimate(note.toString() + line) > noteBudget) break
            note.append(line)
        }
        return note.toString().takeIf { it.contains('\n') }
    }

    private fun firstSentence(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val end = clean.indexOfAny(charArrayOf('.', '?', '!')).let { if (it < 0) clean.length else it + 1 }
        return clean.take(minOf(end, MAX_SNIPPET_CHARS))
    }
}
