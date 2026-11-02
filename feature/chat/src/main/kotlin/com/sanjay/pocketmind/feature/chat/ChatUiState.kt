package com.sanjay.pocketmind.feature.chat

import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message

/**
 * Everything the chat screen draws, in one immutable object.
 *
 * WHY one object? The screen is a pure function of this state ("unidirectional data flow"):
 * events go up to the ViewModel, a new state comes down. There is no second source of truth
 * that could disagree with it.
 */
data class ChatUiState(
    val chatId: String? = null,
    val title: String? = null,
    val messages: List<Message> = emptyList(),
    /** Version info for visible messages that have alternatives (edited / regenerated): "‹ 2 / 3 ›". */
    val branches: Map<String, BranchInfo> = emptyMap(),
    /** The answer currently being generated (not in Room yet), or null when idle. */
    val streamingText: String? = null,
    val isGenerating: Boolean = false,
    val model: ModelUi = ModelUi.Loading,
)

/** Model status as the chat screen needs it. */
sealed interface ModelUi {
    data object Loading : ModelUi

    /** No model downloaded/selected yet: show "Get a model". */
    data object NoModel : ModelUi

    /** The model failed to load; [reason] decides the message and the recovery actions. */
    data class Failed(val reason: FailureReason) : ModelUi

    /** Not in memory (e.g. unloaded under memory pressure); it reloads on the next message. */
    data object Unloaded : ModelUi

    data class Ready(val backendLabel: String) : ModelUi
}

/** One-off events (shown once, e.g. a snackbar), separate from state that is redrawn. */
sealed interface ChatEvent {
    data class Error(val kind: ErrorKind) : ChatEvent
}
