package com.sanjay.pocketmind.feature.chat

import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.core.speech.SpeechError

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
    /** Text shared from another app ("Share → PocketMind"): pre-filled in the input, not sent automatically. */
    val draft: String = "",
    /** Personas to choose from when starting a chat (built-in first). */
    val personas: List<Persona> = emptyList(),
    /** The persona of this chat; for a new chat, the one picked on the welcome screen. Null = default assistant. */
    val personaId: String? = null,
    /** The prompt library, insertable from the input box. */
    val prompts: List<SavedPrompt> = emptyList(),
    val voice: VoiceUi = VoiceUi(),
    /** Id of the answer being read aloud, or null. */
    val speakingMessageId: String? = null,
    val photo: PhotoUi = PhotoUi(),
    val tools: ToolsUi = ToolsUi(),
) {
    val personaName: String? get() = personas.firstOrNull { it.id == personaId }?.name
}

/** File formats a chat can be exported to. */
enum class ExportFormat(val mimeType: String, val extension: String) {
    MARKDOWN("text/markdown", "md"),
    PDF("application/pdf", "pdf"),
}

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

    /** The chat was saved to the file the user picked. */
    data object Exported : ChatEvent

    /** Voice input or read-aloud didn't work. */
    data class VoiceProblem(val kind: SpeechError) : ChatEvent

    /** The picked file couldn't be read as an image. */
    data object PhotoUnreadable : ChatEvent
}

/**
 * The "Add photo" button and the photo waiting to be sent with the next question.
 * [available] is false when the selected model can't read images (the button is then hidden).
 */
data class PhotoUi(
    val available: Boolean = false,
    /** The attached photo (a private copy), or null. */
    val attached: String? = null,
    /** The picked photo is being copied and scaled down. */
    val preparing: Boolean = false,
)

/**
 * The Tools switch: [available] when the selected model can call tools (the switch is hidden
 * otherwise), [enabled] when this chat lets it.
 */
data class ToolsUi(val available: Boolean = false, val enabled: Boolean = true)

/**
 * Voice input state. [transcript] is what the recognizer heard so far; every new transcript gets a
 * new [transcriptVersion], which is how the input box knows to show it (it then belongs to the user,
 * who can edit it and tap Send — nothing is sent automatically).
 */
data class VoiceUi(
    val available: Boolean = false,
    val listening: Boolean = false,
    val transcript: String? = null,
    val transcriptVersion: Int = 0,
)
