package com.sanjay.pocketmind.core.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * [SpeechInput] with Android's ON-DEVICE recognizer (Android 12+). The recognizer is a callback
 * API that must be used on the main thread; `callbackFlow` turns it into a Flow and `awaitClose`
 * releases it when the collector stops (Stop button, screen closed).
 */
internal class AndroidSpeechInput(private val context: Context) : SpeechInput {

    override val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun listen(languageTag: String): Flow<SpeechEvent> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isAvailable) {
            return flowOf(SpeechEvent.Error(SpeechError.UNAVAILABLE))
        }
        return callbackFlow {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        trySend(SpeechEvent.Listening)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults.firstResult()?.let { trySend(SpeechEvent.Partial(it)) }
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results.firstResult()
                        val event = if (text.isNullOrBlank()) {
                            SpeechEvent.Error(SpeechError.NOTHING_HEARD)
                        } else {
                            SpeechEvent.Final(text)
                        }
                        trySend(event)
                        close()
                    }

                    override fun onError(error: Int) {
                        trySend(SpeechEvent.Error(SpeechErrors.map(error)))
                        close()
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
            )
            awaitClose {
                recognizer.cancel()
                recognizer.destroy()
            }
        }.flowOn(Dispatchers.Main.immediate)
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}

/**
 * [SpeechOutput] with Android's TextToSpeech, restricted to voices that work OFFLINE
 * (`Voice.isNetworkConnectionRequired == false`): a network voice would send the answer's text
 * to a server, which breaks the app's promise.
 */
internal class AndroidSpeechOutput(private val context: Context) : SpeechOutput {

    private val _speaking = MutableStateFlow<String?>(null)
    override val speaking: StateFlow<String?> = _speaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null

    override fun speak(id: String, markdown: String): Boolean {
        val text = SpeechText.fromMarkdown(markdown)
        if (text.isBlank()) return false
        // The engine starts asynchronously; the first request waits in [pending] until it is ready.
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                pending?.let { (pid, ptext) -> if (ready) say(pid, ptext) else _speaking.value = null }
                pending = null
            }.also { it.setOnUtteranceProgressListener(progressListener) }
        }
        _speaking.value = id
        if (!ready) {
            pending = id to text
            return true
        }
        return say(id, text).also { if (!it) _speaking.value = null }
    }

    private fun say(id: String, text: String): Boolean {
        val engine = tts ?: return false
        val language = Locale.getDefault().language
        val offline = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        val voice = offline.firstOrNull { it.locale.language == language } ?: offline.firstOrNull()
        if (voice == null) {
            _speaking.value = null
            return false
        }
        engine.voice = voice
        val pieces = SpeechText.chunks(text, TextToSpeech.getMaxSpeechInputLength())
        pieces.forEachIndexed { index, piece ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utterance = if (index == pieces.lastIndex) "$id$LAST" else "$id#$index"
            engine.speak(piece, mode, null, utterance)
        }
        return true
    }

    override fun stop() {
        pending = null
        tts?.stop()
        _speaking.value = null
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId?.endsWith(LAST) == true) _speaking.value = null
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _speaking.value = null
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            _speaking.value = null
        }
    }

    private companion object {
        const val LAST = "#last"
    }
}
