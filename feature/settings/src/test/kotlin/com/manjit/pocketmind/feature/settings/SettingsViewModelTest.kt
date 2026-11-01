package com.manjit.pocketmind.feature.settings

import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.datastore.SettingsDataSource
import com.manjit.pocketmind.core.llm.DefaultPrompts
import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.llm.LlmBackend
import com.manjit.pocketmind.core.model.LlmModelInfo
import com.manjit.pocketmind.core.model.ModelKind
import com.manjit.pocketmind.core.model.ThemeMode
import com.manjit.pocketmind.core.testing.FakeLlmEngine
import com.manjit.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.manjit.pocketmind.core.testing.MainDispatcherRule
import com.manjit.pocketmind.data.models.ModelRepository
import com.manjit.pocketmind.data.models.ModelState
import com.manjit.pocketmind.data.models.ModelStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())
    private val engine = FakeLlmEngine().apply { backend = LlmBackend.CPU }
    private val gemma = LlmModelInfo("g", "Gemma 4 E2B", "", ModelKind.CHAT, "u", 1, "0".repeat(64), 8, "")

    private val models = object : ModelRepository {
        override fun observeModels(): Flow<List<ModelState>> =
            flowOf(listOf(ModelState(gemma, ModelStatus.Downloaded, isSelected = true, fitsInRam = true)))
        override fun deviceRamGb() = 8
        override fun recommendedModelId() = "g"
        override suspend fun download(modelId: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun pause(modelId: String) = Unit
        override suspend fun cancel(modelId: String) = Unit
        override suspend fun delete(modelId: String) = Unit
        override suspend fun select(modelId: String) = Unit
        override fun requiredFreeBytes(modelId: String) = 0L
    }

    private val viewModel by lazy { SettingsViewModel(settings, models, engine) }

    @Test
    fun showsSelectedModelAndTheBackendThatActuallyLoaded() = runTest {
        engine.state.value = EngineState.Ready("/m", LlmBackend.CPU, 10)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("Gemma 4 E2B", viewModel.uiState.value.selectedModelName)
        assertEquals("CPU", viewModel.uiState.value.activeBackend)
    }

    @Test
    fun changesAreSaved() = runTest {
        viewModel.setPreferGpu(false)
        viewModel.setTemperature(1.2f)
        viewModel.setTopK(10)
        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        val s = settings.current()
        assertEquals(false, s.preferGpu)
        assertEquals(1.2f, s.temperature)
        assertEquals(10, s.topK)
        assertEquals(ThemeMode.DARK, s.themeMode)
    }

    @Test
    fun savingTheDefaultPromptKeepsNoCustomPrompt() = runTest {
        viewModel.setSystemPrompt("Be very brief.")
        advanceUntilIdle()
        assertEquals("Be very brief.", settings.current().customSystemPrompt)

        viewModel.setSystemPrompt(DefaultPrompts.SYSTEM)
        advanceUntilIdle()
        assertNull(settings.current().customSystemPrompt)
    }
}
