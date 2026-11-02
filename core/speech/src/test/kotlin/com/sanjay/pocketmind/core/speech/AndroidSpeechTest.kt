package com.sanjay.pocketmind.core.speech

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * The Android speech wrappers against Robolectric's simulated recognizer and TTS engine: we
 * trigger the callbacks a real phone would send and check what comes out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidSpeechTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun reset() {
        ShadowSpeechRecognizer.reset()
        ShadowTextToSpeech.reset()
    }

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `without an on-device recognizer voice input is unavailable`() = runTest {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        val input = AndroidSpeechInput(context)

        assertFalse(input.isAvailable)
        assertEquals(listOf(SpeechEvent.Error(SpeechError.UNAVAILABLE)), input.listen("en-IN").toList())
    }

    @Test
    fun `recognizer callbacks become events and the recognizer is released`() = runTest {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        val input = AndroidSpeechInput(context)
        val events = mutableListOf<SpeechEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            input.listen("hi-IN").toList(events)
        }
        idleMain()

        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertEquals(
            "hi-IN",
            recognizer.lastRecognizerIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE),
        )
        recognizer.triggerOnReadyForSpeech(Bundle())
        recognizer.triggerOnPartialResults(results("drones"))
        recognizer.triggerOnResults(results("drones fly"))
        idleMain()

        assertEquals(
            listOf(SpeechEvent.Listening, SpeechEvent.Partial("drones"), SpeechEvent.Final("drones fly")),
            events,
        )
        assertTrue(job.isCompleted)
        assertTrue(recognizer.isDestroyed)
    }

    @Test
    fun `a recognizer error ends listening with the mapped kind`() = runTest {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        val events = mutableListOf<SpeechEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AndroidSpeechInput(context).listen("en").toList(events)
        }
        idleMain()

        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idleMain()

        assertEquals(listOf(SpeechEvent.Error(SpeechError.NOTHING_HEARD)), events)
    }

    private fun voice(name: String, network: Boolean) =
        Voice(name, Locale.getDefault(), Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, network, emptySet())

    private fun initTts(status: Int = TextToSpeech.SUCCESS) {
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(status)
    }

    @Test
    fun `an answer is spoken with an offline voice, without its Markdown`() {
        ShadowTextToSpeech.addVoice(voice("cloud", network = true))
        ShadowTextToSpeech.addVoice(voice("local", network = false))
        val output = AndroidSpeechOutput(context)

        assertTrue(output.speak("m1", "**Hello** there"))
        assertEquals("m1", output.speaking.value) // while the engine starts
        initTts()

        val tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        assertEquals("Hello there", tts.lastSpokenText)
        assertEquals("local", tts.currentVoice.name)

        tts.utteranceProgressListener.onDone("m1#last")
        assertNull(output.speaking.value)
    }

    @Test
    fun `with only network voices nothing is spoken`() {
        ShadowTextToSpeech.addVoice(voice("cloud", network = true))
        val output = AndroidSpeechOutput(context)

        output.speak("m1", "Hello")
        initTts()

        assertNull(output.speaking.value)
        assertNull(shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)
    }

    @Test
    fun `stop silences the engine and blank text is not spoken`() {
        ShadowTextToSpeech.addVoice(voice("local", network = false))
        val output = AndroidSpeechOutput(context)
        assertFalse(output.speak("m0", "   ")) // nothing to say

        output.speak("m1", "Hello")
        initTts()
        output.speak("m2", "Second") // engine already ready: spoken directly
        assertEquals("Second", shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).lastSpokenText)

        output.stop()
        assertNull(output.speaking.value)
        assertTrue(shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).isStopped)
    }
}
