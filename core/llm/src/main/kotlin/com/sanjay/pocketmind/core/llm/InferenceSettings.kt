package com.sanjay.pocketmind.core.llm

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
    /** Exact size from the catalog; the engine refuses a file of any other size. Null = unknown. */
    val expectedSizeBytes: Long? = null,
    /** The model can read photos (its catalog entry has `supportsImages`). */
    val supportsImages: Boolean = false,
    /** The model can call tools (its catalog entry has `supportsTools`). */
    val supportsTools: Boolean = false,
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

/**
 * Default prompts used across the app.
 *
 * Small on-device models follow short, concrete rules much better than vague ones, so the
 * code rules spell out exactly what a good answer looks like: one complete program in one
 * fenced block with its language, then the expected output. "Answer only the latest
 * message" stops the model from mixing in older topics of a long chat.
 */
object DefaultPrompts {
    const val SYSTEM =
        "You are PocketMind, a helpful assistant that runs fully offline on the user's phone. " +
            "Answer the user's latest message clearly and concisely; use earlier messages only as context. " +
            "Use Markdown for lists and tables. " +
            "When asked for code: think through the logic first, then write ONE complete, runnable program " +
            "in a single fenced code block tagged with its language (for example ```c), including all " +
            "imports or includes and a main function where the language needs one. " +
            "Use correct syntax, clear variable names and short comments. Never leave parts out. " +
            "After the code, briefly explain how it works and show the expected output for an example input. " +
            "When asked to fix code, show the full corrected code and say what was wrong. " +
            "Check arithmetic step by step. " +
            "If you are not sure about something, say so instead of guessing. " +
            "Refuse requests that could cause harm."
}
