package com.manjit.pocketmind.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manjit.pocketmind.core.common.flow.throttleLatest
import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.llm.GenerationStats
import com.manjit.pocketmind.core.llm.LlmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * TEMPORARY (phase 2–3) screen logic that proves the engine works on a real phone:
 * load a model that was pushed with adb, send one prompt, stream the answer.
 * Phase 4 deletes this when the in-app model download exists.
 */
@HiltViewModel
class DebugLlmViewModel @Inject constructor(private val engine: LlmEngine) : ViewModel() {

    data class GenerationUi(
        val output: String = "",
        val isGenerating: Boolean = false,
        val error: String? = null,
        val stats: GenerationStats? = null,
    )

    data class UiState(val engine: EngineState = EngineState.Idle, val generation: GenerationUi = GenerationUi())

    private val generation = MutableStateFlow(GenerationUi())
    private var job: Job? = null

    /** One StateFlow for the screen = engine state + generation state, combined. */
    val uiState: StateFlow<UiState> = combine(engine.state, generation, ::UiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    fun load() {
        viewModelScope.launch { engine.load(DEBUG_MODEL_PATH, preferGpu = true) }
    }

    fun send(prompt: String) {
        if (prompt.isBlank() || job?.isActive == true) return
        job = viewModelScope.launch {
            generation.value = GenerationUi(isGenerating = true)
            engine.newSession(systemPrompt = DEBUG_SYSTEM_PROMPT)
            engine.generate(prompt)
                // Turn the stream of pieces into the growing full text: "Hel", "Hello", "Hello!"
                .runningFold("") { acc, piece -> acc + piece }
                // Update the UI at most every 50 ms (see FlowExtensions.kt for why).
                .throttleLatest(UI_THROTTLE_MS)
                .catch { e -> generation.update { it.copy(error = e.message ?: e::class.simpleName) } }
                .onCompletion { generation.update { it.copy(isGenerating = false, stats = engine.lastStats) } }
                .collect { text -> generation.update { it.copy(output = text) } }
        }
    }

    fun stop() {
        job?.cancel() // cancellation flows down to the engine, which calls cancelProcess()
    }

    companion object {
        /** Where `adb push` puts the model during early development (see PROGRESS.md). */
        const val DEBUG_MODEL_PATH = "/data/local/tmp/llm/model.litertlm"
        const val DEBUG_SYSTEM_PROMPT = "You are a helpful assistant. Answer briefly."
        private const val UI_THROTTLE_MS = 50L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
