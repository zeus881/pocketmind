package com.sanjay.pocketmind.core.llm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The REAL LiteRT-LM engine on a real phone: load, stream, cancel, reuse, unload.
 *
 * It needs a model file, so it skips itself (assumption, not failure) when none is present.
 * How to run on your phone:
 * ```
 * adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/model.litertlm
 * ./gradlew :core:llm:connectedDebugAndroidTest
 * ```
 * Another path can be given with `-Pandroid.testInstrumentationRunnerArguments.modelPath=/sdcard/…`.
 * CI has no phone, so it never runs there; it is listed under "manual steps" in PROGRESS.md.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtLmEngineDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var modelPath: String
    private lateinit var engine: LiteRtLmEngine

    @Before
    fun setUp() {
        modelPath = InstrumentationRegistry.getArguments().getString("modelPath") ?: DEFAULT_MODEL_PATH
        assumeTrue("No model at $modelPath — push one to run this test", File(modelPath).canRead())
        val guard = LoadGuard.forTesting(File(context.cacheDir, "guard-test").apply { deleteRecursively() }) { false }
        engine = LiteRtLmEngine(context, Dispatchers.IO, guard)
    }

    @After
    fun tearDown() = runBlocking {
        if (::engine.isInitialized) engine.unload()
    }

    @Test
    fun loadsStreamsCancelsAndUnloads() = runBlocking {
        engine.load(modelPath, preferGpu = true, expectedSizeBytes = File(modelPath).length())
        val ready = engine.state.value
        assertTrue("Expected Ready, got $ready", ready is EngineState.Ready)

        // Stream a few pieces, then stop collecting: take() cancels the flow, which must stop
        // native generation (cancelProcess) and release the engine lock.
        val session = engine.newSession("You are a test assistant.")
        val firstPieces = engine.generate("Count from 1 to 50, one number per line.", session).take(3).toList()
        assertEquals(3, firstPieces.size)

        // The engine is usable again right after a cancelled generation.
        val again = engine.newSession("You are a test assistant.")
        val answer = engine.generate("Reply with the single word: ready", again).toList().joinToString("")
        assertTrue("Empty answer", answer.isNotBlank())
        assertTrue("Stats missing", (engine.lastStats?.decodeTokensPerSecond ?: 0.0) > 0.0)

        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun wrongSizeIsRefusedWithoutTouchingNativeCode() = runBlocking {
        engine.load(modelPath, preferGpu = true, expectedSizeBytes = File(modelPath).length() + 1)

        assertEquals(EngineState.Failed(modelPath, FailureReason.FILE_DAMAGED), engine.state.value)
    }

    @Test
    fun cpuOnlyLoadReportsCpu() = runBlocking {
        engine.load(modelPath, preferGpu = false)

        assertEquals(LlmBackend.CPU, (engine.state.value as EngineState.Ready).backend)
    }

    /**
     * Only for a model with an image encoder (Gemma 4 E2B): a plain red picture must be described as red.
     * Run with `-Pandroid.testInstrumentationRunnerArguments.vision=true`.
     */
    @Test
    fun aPictureIsSeenByAVisionModel() = runBlocking {
        assumeTrue("Vision test not requested", InstrumentationRegistry.getArguments().getString("vision") == "true")
        val picture = File(context.cacheDir, "red.jpg")
        val red = Bitmap.createBitmap(PICTURE_SIDE, PICTURE_SIDE, Bitmap.Config.ARGB_8888)
        red.eraseColor(Color.RED)
        picture.outputStream().use { red.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }

        engine.load(modelPath, preferGpu = true, vision = true)
        val session = engine.newSession("You are a test assistant.")
        val answer = engine.generate("What colour fills this picture? One word.", session, picture.path)
            .toList()
            .joinToString("")

        assertTrue("Expected red, got: $answer", answer.contains("red", ignoreCase = true))
    }

    private companion object {
        const val PICTURE_SIDE = 256
        const val JPEG_QUALITY = 90
        const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/model.litertlm"
    }
}
