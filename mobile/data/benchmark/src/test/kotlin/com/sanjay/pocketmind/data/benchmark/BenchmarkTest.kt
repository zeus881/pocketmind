package com.sanjay.pocketmind.data.benchmark

import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.model.BenchmarkPromptResult
import com.sanjay.pocketmind.core.model.BenchmarkResult
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkRunnerTest {
    private val engine = FakeLlmEngine()
    private var settings: InferenceSettings? =
        InferenceSettings("/m.litertlm", true, "SYS", SamplingParams(), modelId = "gemma", modelName = "Gemma 4 E2B")
    private val saved = mutableListOf<BenchmarkResult>()

    private val repository = object : BenchmarkRepository {
        override fun observeRuns(): Flow<List<BenchmarkResult>> = MutableStateFlow(saved)
        override suspend fun save(result: BenchmarkResult) {
            saved += result
        }
        override suspend fun delete(id: String) = Unit
    }

    private val metrics = object : DeviceMetrics {
        var memory = 1_000
        var time = 0L
        override fun processMemoryMb() = memory.also { memory += 500 } // grows while running
        override fun elapsedMs() = time.also { time += 100 }
        override fun deviceName() = "Test Phone"
        override fun androidVersion() = "Android 16 (API 36)"
    }

    private fun TestScope.runner() = BenchmarkRunner(
        engine,
        object : InferenceSettingsSource {
            override suspend fun current() = settings
        },
        repository,
        metrics,
        clock = { 1_000L },
        io = UnconfinedTestDispatcher(testScheduler), // must share runTest's scheduler,
    )

    @Test
    fun fullRunMeasuresThreePromptsAndSaves() = runTest {
        val progress = runner().run().toList()

        val result = (progress.last() as BenchmarkProgress.Finished).result
        assertEquals(listOf("short", "medium", "long"), result.prompts.map { it.name })
        assertEquals("GPU", result.backend)
        assertEquals("Gemma 4 E2B", result.modelName)
        assertTrue("peak memory must be sampled", result.peakMemoryMb >= 1_000)
        assertEquals(listOf(result), saved)
        // Cold load: the engine was unloaded before loading.
        assertEquals(1, engine.unloadCount)
        // Every prompt ran in its own fresh, greedy session.
        assertEquals(3, engine.sessions.size)
        assertTrue(engine.sessions.all { it.sampling.topK == 1 })
    }

    @Test
    fun progressGoesLoadingThenPromptsInOrder() = runTest {
        val progress = runner().run().toList()
        assertEquals(BenchmarkProgress.LoadingModel, progress.first())
        assertEquals(
            listOf(1, 2, 3),
            progress.filterIsInstance<BenchmarkProgress.RunningPrompt>().map { it.index },
        )
    }

    @Test
    fun noModelFailsImmediately() = runTest {
        settings = null
        assertEquals(listOf(BenchmarkProgress.Failed(ErrorKind.NO_MODEL)), runner().run().toList())
    }

    @Test
    fun loadFailureIsReported() = runTest {
        engine.failLoad = true
        assertEquals(BenchmarkProgress.Failed(ErrorKind.MODEL_LOAD_FAILED), runner().run().toList().last())
        assertTrue(saved.isEmpty())
    }
}

class BenchmarkReportTest {
    private val result = BenchmarkResult(
        id = "1",
        createdAt = 0,
        modelId = "gemma",
        modelName = "Gemma 4 E2B",
        backend = "GPU",
        loadMs = 8_400,
        prompts = listOf(
            BenchmarkPromptResult("short", 310, 52.1, 12, 600, fromEngine = true),
            BenchmarkPromptResult("medium", 450, 48.3, 140, 3_400, fromEngine = false),
        ),
        peakMemoryMb = 3_120,
        device = "Google Pixel 9",
        androidVersion = "Android 16 (API 36)",
    )

    @Test
    fun reportContainsAllNumbersInUsFormat() {
        val text = BenchmarkReport.format(result, TimeZone.UTC)
        assertTrue(text.contains("1970-01-01 00:00"))
        assertTrue(text.contains("Google Pixel 9"))
        assertTrue(text.contains("Model load: 8.40 s"))
        assertTrue(text.contains("Peak memory: 3120 MB"))
        assertTrue(text.contains("52.1 tok/s"))
        assertTrue(text.contains("Average: first token 0.38 s · 50.2 tok/s"))
    }

    @Test
    fun estimatesAreMarked() {
        val text = BenchmarkReport.format(result, TimeZone.UTC)
        assertTrue(text.contains("48.3 tok/s*"))
        assertTrue(text.contains("* estimated"))
    }
}
