package com.sanjay.pocketmind.core.testing

import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.llm.GenerationStats
import com.sanjay.pocketmind.core.llm.LlmBackend
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.llm.SessionReplacedException
import com.sanjay.pocketmind.core.model.ChatTurn
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

    /** Reason reported when [failLoad] is true. */
    var failReason: FailureReason = FailureReason.INIT_FAILED
    var crashResets = 0
        private set
    val expectedSizes = mutableListOf<Long?>()
    var generationError: Throwable? = null

    /** Virtual-time delay between pieces; lets tests cancel mid-answer. */
    var pieceDelayMs: Long = 0
    var backend: LlmBackend = LlmBackend.GPU

    val loadedPaths = mutableListOf<String>()
    val sessions = mutableListOf<Session>()
    val prompts = mutableListOf<String>()
    var unloadCount = 0
        private set

    override suspend fun load(modelPath: String, preferGpu: Boolean, expectedSizeBytes: Long?) {
        // Same as the real engine: loading the already-loaded model is a no-op.
        if ((state.value as? EngineState.Ready)?.modelPath == modelPath) return
        loadedPaths += modelPath
        expectedSizes += expectedSizeBytes
        sessionId++
        state.value = EngineState.Loading(modelPath)
        state.value = if (failLoad) {
            EngineState.Failed(modelPath, failReason)
        } else {
            EngineState.Ready(modelPath, backend, loadMs = 10)
        }
    }

    override suspend fun newSession(systemPrompt: String, history: List<ChatTurn>, sampling: SamplingParams): Long {
        check(state.value is EngineState.Ready) { "load() must succeed before newSession()" }
        sessions += Session(systemPrompt, history, sampling)
        return ++sessionId
    }

    override fun generate(prompt: String, sessionId: Long): Flow<String> = flow {
        // Same ownership rule as the real engine.
        if (sessionId !=
            this@FakeLlmEngine.sessionId
        ) {
            throw SessionReplacedException(sessionId, this@FakeLlmEngine.sessionId)
        }
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

    override fun resetCrashProtection() {
        crashResets++
    }

    override suspend fun unload() {
        unloadCount++
        sessionId++
        state.value = EngineState.Idle
    }
}
