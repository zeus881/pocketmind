package com.sanjay.pocketmind.core.model

/**
 * The user's theme choice.
 *
 * It lives in `core:model` (pure Kotlin, no Android) because both the settings storage and
 * the UI theme need it, and neither should depend on the other.
 */
enum class ThemeMode {
    /** Follow the phone's system-wide dark mode switch. */
    SYSTEM,
    LIGHT,
    DARK,
}
