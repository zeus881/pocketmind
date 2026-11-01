package com.manjit.pocketmind.debug

import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.testing.FakeLlmEngine
import com.manjit.pocketmind.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugLlmViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val engine = FakeLlmEngine()

    // Lazy on purpose: the ViewModel must be created AFTER MainDispatcherRule has replaced
    // Dispatchers.Main, because viewModelScope captures the Main dispatcher when created.
    private val viewModel by lazy { DebugLlmViewModel(engine) }

    @Test
    fun `load uses the adb debug path`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(DebugLlmViewModel.DEBUG_MODEL_PATH), engine.loadedPaths)
        assertTrue(viewModel.uiState.value.engine is EngineState.Ready)
    }

    @Test
    fun `send streams the full answer and records stats`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.load()
        viewModel.send("Hi")
        advanceUntilIdle()

        val generation = viewModel.uiState.value.generation
        assertEquals("Hello from fake", generation.output)
        assertFalse(generation.isGenerating)
        assertEquals(12.4, generation.stats!!.decodeTokensPerSecond, 0.0)
        assertEquals(listOf("Hi"), engine.prompts)
    }

    @Test
    fun `stop cancels mid-answer and keeps the partial text`() = runTest(mainRule.testDispatcher) {
        engine.pieceDelayMs = 100
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.load()
        viewModel.send("Hi")
        advanceTimeBy(150) // first piece emitted at 100 ms, second would come at 200 ms
        viewModel.stop()
        advanceUntilIdle()

        val generation = viewModel.uiState.value.generation
        assertEquals("Hello", generation.output)
        assertFalse(generation.isGenerating)
    }

    @Test
    fun `generation error is shown`() = runTest(mainRule.testDispatcher) {
        engine.generationError = IllegalStateException("native failure")
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.load()
        viewModel.send("Hi")
        advanceUntilIdle()

        assertEquals("native failure", viewModel.uiState.value.generation.error)
    }
}
