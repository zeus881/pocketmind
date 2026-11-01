package com.manjit.pocketmind.core.llm

import com.manjit.pocketmind.core.model.ChatTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's ONLY doorway to the on-device language model.
 *
 * WHY an interface? (1) Tests use `FakeLlmEngine` instead of a 2.6 GB model, so ViewModel
 * tests run in milliseconds on any laptop or CI machine. (2) If Google replaces LiteRT-LM
 * (as happened to the MediaPipe LLM API), only one class changes — no screen does.
 *
 * Contract:
 * - One model in memory at a time ([load] replaces the previous one).
 * - One generation at a time: a second [generate] waits until the first finishes.
 * - Cancelling the collector of [generate] stops the native generation.
 *
 * Differences from the sketch in CLAUDE.md (both needed by the real API):
 * - [newSession] is `suspend` because it must wait for a running generation to finish
 *   before it replaces the conversation, and it takes [SamplingParams] from settings.
 * - [lastStats] and [contextWindowTokens] expose performance data and the context size.
 */
interface LlmEngine {
    /** Current model lifecycle state; collect it to show loading/ready/failed UI. */
    val state: StateFlow<EngineState>

    /** Stats of the most recent completed or stopped generation, or null if none yet. */
    val lastStats: GenerationStats?

    /** Maximum tokens (prompt + history + answer) one conversation can hold. */
    val contextWindowTokens: Int

    /**
     * Changes every time the conversation is replaced (newSession, load, unload). Several
     * features share the one engine (chat, benchmark, translate, summarize), so a caller that
     * built a session remembers this id and rebuilds if someone else has replaced it since.
     */
    val sessionId: Long

    /** Loads the model, trying GPU first when [preferGpu] and falling back to CPU. No-op if already loaded. */
    suspend fun load(modelPath: String, preferGpu: Boolean)

    /**
     * Starts a fresh conversation. [history] restores earlier turns when a saved chat is reopened.
     * Returns the new session's id: pass it to [generate] so the prompt can only run in THIS session.
     */
    suspend fun newSession(
        systemPrompt: String,
        history: List<ChatTurn> = emptyList(),
        sampling: SamplingParams = SamplingParams(),
    ): Long

    /**
     * Sends [prompt] in session [sessionId] and streams the answer piece by piece.
     * Throws [SessionReplacedException] if another feature replaced that session in the meantime
     * (checked under the engine lock, so there is no gap between the check and the generation).
     */
    fun generate(prompt: String, sessionId: Long): Flow<String>

    /** Frees the model's memory (also called when Android is low on memory). */
    suspend fun unload()
}

/**
 * The conversation a caller prepared was replaced (by another feature, a model reload or
 * memory-pressure unload) before its prompt could run. Callers rebuild the session and retry.
 */
class SessionReplacedException(expected: Long, actual: Long) :
    IllegalStateException("Session $expected was replaced by session $actual")
