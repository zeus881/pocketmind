package com.sanjay.pocketmind.feature.benchmark

import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.model.BenchmarkResult
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.benchmark.BenchmarkRepository
import com.sanjay.pocketmind.data.benchmark.BenchmarkRunner
import com.sanjay.pocketmind.data.benchmark.DeviceMetrics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val runs = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    private var settings: InferenceSettings? = InferenceSettings("/m", true, "S", SamplingParams(), "id", "Model")

    private val repository = object : BenchmarkRepository {
        override fun observeRuns(): Flow<List<BenchmarkResult>> = runs
        override suspend fun save(result: BenchmarkResult) {
            runs.value = listOf(result) + runs.value
        }
        override suspend fun delete(id: String) {
            runs.value = runs.value.filterNot { it.id == id }
        }
    }

    private val metrics = object : DeviceMetrics {
        override fun processMemoryMb() = 2_000
        override fun elapsedMs() = 0L
        override fun deviceName() = "Phone"
        override fun androidVersion() = "Android 16"
    }

    private val viewModel by lazy {
        val runner = BenchmarkRunner(
            FakeLlmEngine(),
            object : InferenceSettingsSource {
                override suspend fun current() = settings
            },
            repository,
            metrics,
            clock = { 0L },
            io = UnconfinedTestDispatcher(mainRule.testDispatcher.scheduler),
        )
        BenchmarkViewModel(runner, repository)
    }

    @Test
    fun runningSavesAResultAndEndsIdle() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRunning)
        assertEquals(1, state.runs.size)
        assertEquals(3, state.runs.single().prompts.size)
    }

    @Test
    fun noModelShowsAnError() = runTest {
        settings = null
        viewModel.start()
        assertEquals(BenchmarkEvent.Error(ErrorKind.NO_MODEL), viewModel.events.first())
    }

    @Test
    fun shareProducesReadableText() = runTest {
        viewModel.start()
        advanceUntilIdle()
        viewModel.share(runs.value.single())

        val event = viewModel.events.first() as BenchmarkEvent.Share
        assertTrue(event.text.startsWith("PocketMind benchmark"))
    }

    @Test
    fun deleteRemovesTheRun() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.start()
        advanceUntilIdle()
        viewModel.delete(runs.value.single())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.runs.isEmpty())
    }
}
