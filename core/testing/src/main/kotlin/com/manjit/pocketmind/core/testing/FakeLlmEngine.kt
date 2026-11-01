package com.manjit.pocketmind.core.testing

import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.llm.FailureReason
import com.manjit.pocketmind.core.llm.GenerationStats
import com.manjit.pocketmind.core.llm.LlmBackend
import com.manjit.pocketmind.core.llm.LlmEngine
import com.manjit.pocketmind.core.llm.SamplingParams
import com.manjit.pocketmind.core.model.ChatTurn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * A pretend [LlmEngine] for unit tests: no native code, no model file, instant answers.
 *
 * Tests configure what it does ([responsePieces], [failLoad], [generationError],
 * [pieceDelayMs]) and then check what the code under test asked it to do ([loadedPaths],
 * [sessions], [prompts]). This is the "test double" that makes our ViewModel and repository
 * tests possible on a laptop or in CI.
 */
class FakeLlmEngine(var responsePieces: List<String> = listOf("Hello", " from", " fake")) : LlmEngine {

    /** Records one newSession() call. */
    data class Session(val systemPrompt: String, val history: List<ChatTurn>, val sampling: SamplingParams)

    override val state = MutableStateFlow<EngineState>(EngineState.Idle)
    override var lastStats: GenerationStats? = null
    override var contextWindowTokens: Int = 4096
    override var sessionId: Long = 0
        private set

    var failLoad: Boolean = false
    var generationError: Throwable? = null

    /** Virtual-time delay between pieces; lets tests cancel mid-answer. */
    var pieceDelayMs: Long = 0
    var backend: LlmBackend = LlmBackend.GPU

    val loadedPaths = mutableListOf<String>()
    val sessions = mutableListOf<Session>()
    val prompts = mutableListOf<String>()
    var unloadCount = 0
        private set

    override suspend fun load(modelPath: String, preferGpu: Boolean) {
        // Same as the real engine: loading the already-loaded model is a no-op.
        if ((state.value as? EngineState.Ready)?.modelPath == modelPath) return
        loadedPaths += modelPath
        sessionId++
        state.value = EngineState.Loading(modelPath)
        state.value = if (failLoad) {
            EngineState.Failed(modelPath, FailureReason.INIT_FAILED)
        } else {
            EngineState.Ready(modelPath, backend, loadMs = 10)
        }
    }

    override suspend fun newSession(systemPrompt: String, history: List<ChatTurn>, sampling: SamplingParams) {
        check(state.value is EngineState.Ready) { "load() must succeed before newSession()" }
        sessions += Session(systemPrompt, history, sampling)
        sessionId++
    }

    override fun generate(prompt: String): Flow<String> = flow {
        prompts += prompt
        for (piece in responsePieces) {
            if (pieceDelayMs > 0) delay(pieceDelayMs)
            emit(piece)
        }
        generationError?.let { throw it }
        lastStats = GenerationStats(
            timeToFirstTokenMs = 5,
            decodeTokensPerSecond = 12.4,
            generatedTokens = responsePieces.size,
            backend = backend,
            fromEngine = true,
        )
    }

    override suspend fun unload() {
        unloadCount++
        sessionId++
        state.value = EngineState.Idle
    }
}
