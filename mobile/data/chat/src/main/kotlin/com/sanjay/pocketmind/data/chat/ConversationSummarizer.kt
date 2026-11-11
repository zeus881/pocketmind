package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.model.ChatTurn
import com.sanjay.pocketmind.core.model.TurnRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import timber.log.Timber

/**
 * Writes a short summary of the part of a chat that no longer fits in the model's memory.
 * An interface so the repository can be tested with a predictable fake.
 */
fun interface ConversationSummarizer {
    /**
     * Summarizes [turns], continuing [previousSummary] when there is one (only the turns that
     * newly fell out of the window are passed). Returns null if no summary could be written;
     * the caller then falls back to the cheap extractive note.
     */
    suspend fun summarize(previousSummary: String?, turns: List<ChatTurn>): String?
}

/**
 * [ConversationSummarizer] that asks the on-device model itself.
 *
 * WHY a model-written summary: v1 kept only the first sentence of each dropped turn. That's
 * free, but it loses what usually matters later — the numbers, names, code and decisions in the
 * middle of an answer. A summary costs a few seconds of generation, so the repository caches it
 * and only extends it when more turns fall out of the window.
 *
 * It uses its own short session with precise sampling (low temperature): summaries must be
 * faithful, not creative. The chat's own session is rebuilt right afterwards by the caller.
 */
class LlmConversationSummarizer(private val engine: LlmEngine) : ConversationSummarizer {

    @Suppress("TooGenericExceptionCaught") // native generation can fail in many ways; we fall back
    override suspend fun summarize(previousSummary: String?, turns: List<ChatTurn>): String? {
        if (turns.isEmpty()) return previousSummary
        return try {
            val session = engine.newSession(ChatSummaryPrompt.SYSTEM, emptyList(), SamplingParams.Precise)
            engine.generate(ChatSummaryPrompt.build(previousSummary, turns), session)
                .toList()
                .joinToString("")
                .trim()
                .takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Conversation summary failed; using the extractive note")
            null
        }
    }
}

/** The summary prompt. Pure text building, unit-tested. */
object ChatSummaryPrompt {
    const val SYSTEM =
        "You summarize conversations so they can be continued later. Write one compact summary in plain text, " +
            "at most 120 words. Keep names, numbers, decisions, code details and open questions. " +
            "Only include what was actually said."

    /** Each message is clipped so that a long answer can't push the prompt out of the context window. */
    const val MAX_CHARS_PER_TURN = 400

    /** The whole transcript is kept under this size; when longer, the OLDEST messages are left out. */
    const val MAX_TRANSCRIPT_CHARS = 6_000

    fun build(previousSummary: String?, turns: List<ChatTurn>): String {
        val lines = turns.map { turn ->
            val who = if (turn.role == TurnRole.USER) "User" else "Assistant"
            val text = turn.text.replace(Regex("\\s+"), " ").trim()
            "$who: ${if (text.length > MAX_CHARS_PER_TURN) text.take(MAX_CHARS_PER_TURN) + "…" else text}"
        }
        // Keep the newest lines that fit: they are closest to what the chat continues with.
        val kept = ArrayDeque<String>()
        var size = 0
        for (line in lines.asReversed()) {
            if (size + line.length > MAX_TRANSCRIPT_CHARS) break
            kept.addFirst(line)
            size += line.length + 1
        }
        return buildString {
            if (!previousSummary.isNullOrBlank()) {
                append("Summary so far:\n").append(previousSummary.trim()).append("\n\n")
                append("More of the conversation:\n")
            } else {
                append("Conversation:\n")
            }
            append(kept.joinToString("\n"))
            append("\n\nWrite the updated summary.")
        }
    }
}
