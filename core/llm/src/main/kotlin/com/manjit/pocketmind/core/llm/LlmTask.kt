package com.manjit.pocketmind.core.llm

import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.common.result.ErrorKind
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold

/**
 * Helper for ONE-SHOT tasks (summarize a chunk, translate a text): load the selected model,
 * start a FRESH conversation, send one prompt.
 *
 * WHY a fresh session every time? A task must not "remember" earlier chats or earlier tasks:
 * old history would leak into a translation, waste context window, and make results depend on
 * what the user did before. It also changes [LlmEngine.sessionId], so the chat knows to rebuild
 * its own conversation afterwards.
 */
class LlmTask @Inject constructor(private val engine: LlmEngine, private val settingsSource: InferenceSettingsSource) {
    /** Context window of the loaded model (for sizing chunks). */
    val contextWindowTokens: Int get() = engine.contextWindowTokens

    /** Loads the selected model if needed. NO_MODEL / MODEL_LOAD_FAILED on failure. */
    suspend fun ensureLoaded(): AppResult<InferenceSettings> {
        val settings = settingsSource.current() ?: return AppResult.Failure(ErrorKind.NO_MODEL)
        engine.load(settings.modelPath, settings.preferGpu)
        return if (engine.state.value is EngineState.Ready) {
            AppResult.Success(settings)
        } else {
            AppResult.Failure(ErrorKind.MODEL_LOAD_FAILED)
        }
    }

    /** Streams the answer to [prompt] in a brand-new session. Call [ensureLoaded] first. */
    fun stream(systemPrompt: String, prompt: String, sampling: SamplingParams = SamplingParams.Precise): Flow<String> =
        flow {
            val session = engine.newSession(systemPrompt, emptyList(), sampling)
            emitAll(engine.generate(prompt, session))
        }

    /** Like [stream] but waits for the whole answer. */
    suspend fun complete(
        systemPrompt: String,
        prompt: String,
        sampling: SamplingParams = SamplingParams.Precise,
    ): String = stream(systemPrompt, prompt, sampling)
        .fold(StringBuilder()) { acc, piece -> acc.append(piece) }
        .toString()
        .trim()
}
