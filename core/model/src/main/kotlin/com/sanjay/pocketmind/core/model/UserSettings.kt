package com.sanjay.pocketmind.core.model

/**
 * Everything the user can change, with safe defaults. Stored with DataStore (core:datastore).
 *
 * [customSystemPrompt] = null means "use the built-in default prompt", so improving the
 * default in a new app version reaches users who never edited it.
 */
data class UserSettings(
    val selectedModelId: String? = null,
    val onboardingDone: Boolean = false,
    val allowMobileData: Boolean = false,
    val preferGpu: Boolean = true,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topK: Int = DEFAULT_TOP_K,
    val customSystemPrompt: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    /** How long the app may stay in the background before the lock comes back (0 = immediately). */
    val appLockTimeoutSeconds: Int = DEFAULT_LOCK_TIMEOUT_SECONDS,
) {
    companion object {
        /**
         * 0.4 rather than the usual 0.7: a 1–2 B model makes noticeably fewer syntax and logic
         * mistakes in code and maths with less randomness, and answers still read naturally.
         */
        const val DEFAULT_TEMPERATURE = 0.4f
        const val DEFAULT_TOP_K = 40
        const val MIN_TEMPERATURE = 0.1f
        const val MAX_TEMPERATURE = 1.5f
        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 100

        /**
         * 30 s by default: switching to another app for a moment (or picking a PDF in the system
         * file picker) shouldn't demand a fingerprint, but a phone left on a table locks soon.
         */
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 30

        /** The choices offered in Settings, in seconds. */
        val LOCK_TIMEOUT_OPTIONS = listOf(0, 30, 60, 300)
    }
}
