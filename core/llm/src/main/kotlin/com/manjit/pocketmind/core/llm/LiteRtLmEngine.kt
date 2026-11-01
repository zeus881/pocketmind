package com.manjit.pocketmind.core.llm

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.manjit.pocketmind.core.common.dispatchers.Dispatcher
import com.manjit.pocketmind.core.common.dispatchers.PmDispatchers
import com.manjit.pocketmind.core.model.ChatTurn
import com.manjit.pocketmind.core.model.TurnRole
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [LlmEngine] implemented with Google's LiteRT-LM runtime. The ONLY class in the app that
 * imports `com.google.ai.edge.litertlm`.
 *
 * Threading model:
 * - Every native call (initialize, createConversation, generation) is blocking and slow,
 *   so it runs on the injected IO dispatcher — never on the main (UI) thread.
 * - A single [Mutex] guards the native objects. Only one of load / newSession / generate /
 *   unload can touch them at a time, so we can never close a conversation in the middle of
 *   a generation (which would crash in native code).
 *
 * `@Singleton`: a loaded model uses gigabytes of RAM, so the whole app shares ONE engine.
 */
@OptIn(ExperimentalApi::class)
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext context: Context,
    @Dispatcher(PmDispatchers.IO) private val io: CoroutineDispatcher,
) : LlmEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    // @Volatile: written on an IO thread, read by the UI thread; volatile guarantees the
    // reader sees the newest value.
    @Volatile
    override var lastStats: GenerationStats? = null
        private set

    override val contextWindowTokens: Int = CONTEXT_WINDOW_TOKENS

    /** GPU backends compile their programs on first load; caching them here makes the next load faster. */
    private val cacheDir: String = context.cacheDir.absolutePath

    private val lock = Mutex()

    // The fields below are only touched while holding [lock].
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null
    private var loadedPreferGpu: Boolean? = null
    private var loadedBackend: LlmBackend? = null

    init {
        // Lets us read exact time-to-first-token and decode speed from the runtime
        // (Conversation.getBenchmarkInfo) instead of only estimating them.
        ExperimentalFlags.enableBenchmark = true
    }

    override suspend fun load(modelPath: String, preferGpu: Boolean) = withContext(io) {
        lock.withLock {
            // Already loaded with the same settings: nothing to do (cheap to call on every screen open).
            if (engine != null && loadedPath == modelPath && loadedPreferGpu == preferGpu) return@withLock

            closeAllLocked()
            if (!File(modelPath).canRead()) {
                _state.value = EngineState.Failed(modelPath, FailureReason.FILE_NOT_FOUND)
                return@withLock
            }
            _state.value = EngineState.Loading(modelPath)
            val start = SystemClock.elapsedRealtime()

            for (backend in EngineMath.backendOrder(preferGpu)) {
                val created = tryCreateEngine(modelPath, backend) ?: continue
                engine = created
                loadedPath = modelPath
                loadedPreferGpu = preferGpu
                loadedBackend = backend
                val loadMs = SystemClock.elapsedRealtime() - start
                Timber.i("Model loaded on %s in %d ms", backend, loadMs)
                _state.value = EngineState.Ready(modelPath, backend, loadMs)
                return@withLock
            }
            _state.value = EngineState.Failed(modelPath, FailureReason.INIT_FAILED)
        }
    }

    /**
     * Creates and initializes an engine on one backend, or returns null if that backend fails.
     * GPU init fails on phones without a usable OpenCL driver — that is expected, not a bug,
     * and is exactly why we fall back to CPU.
     */
    @Suppress("TooGenericExceptionCaught") // Native code can throw several unrelated exception types.
    private fun tryCreateEngine(modelPath: String, backend: LlmBackend): Engine? {
        val candidate = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = backend.toLiteRt(),
                maxNumTokens = CONTEXT_WINDOW_TOKENS,
                cacheDir = cacheDir,
            ),
        )
        return try {
            candidate.initialize() // the slow part: maps the model file and prepares the backend
            candidate
        } catch (e: Exception) {
            Timber.w(e, "Backend %s failed, trying the next one", backend)
            runCatching { candidate.close() } // free whatever native memory was already allocated
            null
        }
    }

    override suspend fun newSession(systemPrompt: String, history: List<ChatTurn>, sampling: SamplingParams) =
        withContext(io) {
            lock.withLock {
                val loaded = checkNotNull(engine) { "load() must succeed before newSession()" }
                conversation?.close()
                conversation = null
                conversation = loaded.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        // Restores an old chat: the model "remembers" these turns.
                        initialMessages = history.map { it.toLiteRt() },
                        samplerConfig = SamplerConfig(
                            topK = sampling.topK,
                            topP = sampling.topP,
                            temperature = sampling.temperature,
                            seed = sampling.seed,
                        ),
                        // Models like Qwen3 can "think out loud" before answering. That doubles the
                        // latency on a phone and would show raw reasoning in the chat, so we turn it off.
                        thinkingConfig = ThinkingConfig(enableThinking = false),
                    ),
                )
            }
        }

    /**
     * Streams the answer. Built with `flow {}` + `flowOn(io)`:
     * - `flow {}` is cold: nothing happens until someone collects.
     * - `flowOn(io)` moves the producer (this block) to the IO dispatcher. We must NOT use
     *   `withContext` inside `flow {}` — that breaks the Flow's "context preservation" rule
     *   and throws at runtime.
     */
    override fun generate(prompt: String): Flow<String> = flow {
        lock.withLock {
            val conv = checkNotNull(conversation) { "newSession() must be called before generate()" }
            val start = SystemClock.elapsedRealtime()
            var firstPieceAt = -1L
            var pieces = 0
            try {
                conv.sendMessageAsync(prompt).collect { message ->
                    val text = message.text()
                    if (text.isNotEmpty()) {
                        if (firstPieceAt < 0) firstPieceAt = SystemClock.elapsedRealtime()
                        pieces++
                        emit(text)
                    }
                }
            } catch (e: CancellationException) {
                // IMPORTANT (verified in LiteRT-LM 0.19 bytecode): cancelling the library's Flow
                // does NOT stop the native generation. Without this call the model would keep
                // running in the background, burning battery and blocking the next request.
                conv.cancelProcess()
                throw e
            } finally {
                if (firstPieceAt >= 0) {
                    lastStats = buildStats(conv, start, firstPieceAt, pieces, SystemClock.elapsedRealtime())
                }
            }
        }
    }.flowOn(io)

    override suspend fun unload() = withContext(io) {
        lock.withLock {
            closeAllLocked()
            _state.value = EngineState.Idle
        }
    }

    /** Prefers the runtime's exact counters; falls back to our own piece-counting estimate. */
    private fun buildStats(
        conv: Conversation,
        start: Long,
        firstPieceAt: Long,
        pieces: Int,
        end: Long,
    ): GenerationStats {
        val backend = loadedBackend ?: LlmBackend.CPU
        val info = runCatching { conv.getBenchmarkInfo() }.getOrNull()
        return if (info != null && info.lastDecodeTokensPerSecond > 0) {
            GenerationStats(
                timeToFirstTokenMs = (info.timeToFirstTokenInSecond * MILLIS_PER_SECOND).toLong(),
                decodeTokensPerSecond = info.lastDecodeTokensPerSecond,
                generatedTokens = info.lastDecodeTokenCount,
                backend = backend,
                fromEngine = true,
            )
        } else {
            GenerationStats(
                timeToFirstTokenMs = firstPieceAt - start,
                decodeTokensPerSecond = EngineMath.estimateDecodeRate(pieces, firstPieceAt, end),
                generatedTokens = pieces,
                backend = backend,
                fromEngine = false,
            )
        }
    }

    /** Releases native memory. Order matters: the conversation belongs to the engine. */
    private fun closeAllLocked() {
        runCatching { conversation?.close() }.onFailure { Timber.w(it, "conversation.close failed") }
        runCatching { engine?.close() }.onFailure { Timber.w(it, "engine.close failed") }
        conversation = null
        engine = null
        loadedPath = null
        loadedPreferGpu = null
        loadedBackend = null
    }

    private fun LlmBackend.toLiteRt(): Backend = when (this) {
        LlmBackend.GPU -> Backend.GPU()
        LlmBackend.CPU -> Backend.CPU()
    }

    private fun ChatTurn.toLiteRt(): Message = when (role) {
        TurnRole.USER -> Message.user(text)
        TurnRole.ASSISTANT -> Message.model(text)
    }

    /** A streamed Message holds a list of Content parts; for chat we only need the text parts. */
    private fun Message.text(): String = contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString("") { it.text }

    private companion object {
        /**
         * Tokens of KV cache the engine reserves. Bigger = longer chats but more RAM.
         * 4096 is a safe size for 6–8 GB phones with a 2B model.
         */
        const val CONTEXT_WINDOW_TOKENS = 4096
        const val MILLIS_PER_SECOND = 1000.0
    }
}
