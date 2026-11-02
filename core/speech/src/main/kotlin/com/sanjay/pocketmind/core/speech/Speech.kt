package com.sanjay.pocketmind.core.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Speech to text, ON THE DEVICE ONLY. An interface so the chat can be tested without a microphone.
 *
 * Privacy rule: audio never leaves the phone. Android 12+ offers a recognizer guaranteed to run on
 * the device (`SpeechRecognizer.createOnDeviceSpeechRecognizer`). Older Android versions only offer
 * "prefer offline", which may silently use Google's servers, so there voice input is unavailable.
 */
interface SpeechInput {
    /** True when an on-device recognizer exists on this phone (Android 12+ with a speech service). */
    val isAvailable: Boolean

    /** Listens once. Emits Listening, then Partial texts, then Final or Error, and completes. Cancel = stop. */
    fun listen(languageTag: String): Flow<SpeechEvent>
}

sealed interface SpeechEvent {
    data object Listening : SpeechEvent

    data class Partial(val text: String) : SpeechEvent

    data class Final(val text: String) : SpeechEvent

    data class Error(val kind: SpeechError) : SpeechEvent
}

enum class SpeechError {
    /** No on-device recognizer, or it isn't installed for this language. */
    UNAVAILABLE,

    /** The microphone permission is missing. */
    PERMISSION,

    /** Nothing understandable was heard. */
    NOTHING_HEARD,

    /** Anything else (audio problem, recognizer busy). */
    FAILED,
}

/** Text to speech with OFFLINE voices only. */
interface SpeechOutput {
    /** Id of the text being spoken (e.g. a message id), or null when silent. */
    val speaking: StateFlow<String?>

    /** Reads [markdown] aloud (formatting removed). Returns false if no offline voice is available. */
    fun speak(id: String, markdown: String): Boolean

    fun stop()
}
