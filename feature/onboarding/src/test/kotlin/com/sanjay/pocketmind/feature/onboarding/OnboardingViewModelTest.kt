package com.sanjay.pocketmind.feature.onboarding

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.models.ModelRepository
import com.sanjay.pocketmind.data.models.ModelState
import com.sanjay.pocketmind.data.models.ModelStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val gemma = LlmModelInfo(
        "gemma-4-e2b", "Gemma 4 E2B", "", ModelKind.CHAT, "https://huggingface.co/x", 2_588_147_712,
        "0".repeat(
            64,
        ),
        8, "",
    )

    private val repo = object : ModelRepository {
        override fun observeModels(): Flow<List<ModelState>> =
            flowOf(listOf(ModelState(gemma, ModelStatus.NotDownloaded, isSelected = false, fitsInRam = true)))
        override fun deviceRamGb() = 8
        override fun recommendedModelId() = "gemma-4-e2b"
        override suspend fun download(modelId: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun pause(modelId: String) = Unit
        override suspend fun cancel(modelId: String) = Unit
        override suspend fun delete(modelId: String) = Unit
        override suspend fun select(modelId: String) = Unit
        override suspend fun verify(modelId: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun requiredFreeBytes(modelId: String) = 0L
    }

    private val settings by lazy {
        SettingsDataSource(InMemoryPreferencesDataStore())
    }
    private val viewModel by lazy { OnboardingViewModel(repo, settings) }

    @Test
    fun showsRecommendedModelForThisPhone() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("Gemma 4 E2B", state.recommendedName)
        assertEquals(8, state.deviceRamGb)
        assertEquals(2_588_147_712, state.recommendedSizeBytes)
    }

    @Test
    fun finishSavesOnboardingDoneAndEmitsOnce() = runTest {
        viewModel.finish()
        viewModel.finished.first()
        assertTrue(settings.current().onboardingDone)
    }
}
