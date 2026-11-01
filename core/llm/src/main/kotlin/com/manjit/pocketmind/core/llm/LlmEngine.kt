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

    /** Loads the model, trying GPU first when [preferGpu] and falling back to CPU. No-op if already loaded. */
    suspend fun load(modelPath: String, preferGpu: Boolean)

    /** Starts a fresh conversation. [history] restores earlier turns when a saved chat is reopened. */
    suspend fun newSession(
        systemPrompt: String,
        history: List<ChatTurn> = emptyList(),
        sampling: SamplingParams = SamplingParams(),
    )

    /** Sends [prompt] in the current conversation and streams the answer piece by piece. */
    fun generate(prompt: String): Flow<String>

    /** Frees the model's memory (also called when Android is low on memory). */
    suspend fun unload()
}
