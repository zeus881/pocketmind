package com.manjit.pocketmind.core.llm

/**
 * Everything needed to run a conversation: which model file, which hardware, which
 * system prompt and sampling values. Repositories ask an [InferenceSettingsSource] for it
 * right before each request, so a settings change applies to the next message.
 */
data class InferenceSettings(
    val modelPath: String,
    val preferGpu: Boolean,
    val systemPrompt: String,
    val sampling: SamplingParams,
    /** Catalog id and display name, e.g. for labelling benchmark results. */
    val modelId: String = "",
    val modelName: String = "",
)

/**
 * Where [InferenceSettings] come from. An interface so that data modules don't depend on how
 * settings are stored: phase 3 binds an adb-debug implementation, later phases bind one
 * backed by DataStore and the model manager.
 */
interface InferenceSettingsSource {
    /** Current settings, or null when no model is available (the UI then offers a download). */
    suspend fun current(): InferenceSettings?
}

/** Default prompts used across the app. */
object DefaultPrompts {
    const val SYSTEM =
        "You are PocketMind, a helpful assistant that runs fully offline on the user's phone. " +
            "Answer clearly and concisely. Use Markdown for lists, tables and code. " +
            "If you are not sure about something, say so instead of guessing. " +
            "Refuse requests that could cause harm."
}
