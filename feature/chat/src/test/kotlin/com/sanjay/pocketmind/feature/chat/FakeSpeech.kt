package com.sanjay.pocketmind.feature.chat

import com.sanjay.pocketmind.core.speech.SpeechEvent
import com.sanjay.pocketmind.core.speech.SpeechInput
import com.sanjay.pocketmind.core.speech.SpeechOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Plays a scripted list of recognizer events; records the languages asked for. */
class FakeSpeechInput(var available: Boolean = true) : SpeechInput {
    var events: List<SpeechEvent> = listOf(
        SpeechEvent.Listening,
        SpeechEvent.Partial("how do"),
        SpeechEvent.Final("how do drones fly"),
    )
    val languages = mutableListOf<String>()

    override val isAvailable: Boolean get() = available

    override fun listen(languageTag: String): Flow<SpeechEvent> = flow {
        languages += languageTag
        events.forEach { emit(it) }
    }
}

class FakeSpeechOutput : SpeechOutput {
    override val speaking = MutableStateFlow<String?>(null)
    var hasOfflineVoice = true
    val spoken = mutableListOf<String>()

    override fun speak(id: String, markdown: String): Boolean {
        if (!hasOfflineVoice) return false
        spoken += markdown
        speaking.value = id
        return true
    }

    override fun stop() {
        speaking.value = null
    }
}
