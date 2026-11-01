package com.manjit.pocketmind.feature.models

import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.core.datastore.SettingsDataSource
import com.manjit.pocketmind.core.model.LlmModelInfo
import com.manjit.pocketmind.core.model.ModelKind
import com.manjit.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.manjit.pocketmind.core.testing.MainDispatcherRule
import com.manjit.pocketmind.data.models.ModelRepository
import com.manjit.pocketmind.data.models.ModelState
import com.manjit.pocketmind.data.models.ModelStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun info(id: String, kind: ModelKind = ModelKind.CHAT) =
        LlmModelInfo(id, id, "", kind, "https://huggingface.co/x", 1_000, "0".repeat(64), 4, "Apache-2.0")

    private class FakeRepo : ModelRepository {
        val models = MutableStateFlow<List<ModelState>>(emptyList())
        var downloadResult: AppResult<Unit> = AppResult.Success(Unit)
        val downloads = mutableListOf<String>()
        val paused = mutableListOf<String>()

        override fun observeModels(): Flow<List<ModelState>> = models
        override fun deviceRamGb() = 8
        override fun recommendedModelId() = "chat"
        override suspend fun download(modelId: String): AppResult<Unit> {
            downloads += modelId
            return downloadResult
        }
        override fun pause(modelId: String) {
            paused += modelId
        }
        override suspend fun cancel(modelId: String) = Unit
        override suspend fun delete(modelId: String) = Unit
        override suspend fun select(modelId: String) = Unit
        override fun requiredFreeBytes(modelId: String) = 1_200L
    }

    private val repo = FakeRepo()
    private val settings by lazy {
        SettingsDataSource(InMemoryPreferencesDataStore())
    }
    private val viewModel by lazy { ModelsViewModel(repo, settings) }

    @Test
    fun splitsChatAndEmbeddingModelsAndShowsRecommendation() = runTest {
        repo.models.value = listOf(
            ModelState(info("chat"), ModelStatus.NotDownloaded, isSelected = false, fitsInRam = true),
            ModelState(
                info("embed", ModelKind.EMBEDDING),
                ModelStatus.Downloaded,
                isSelected = false,
                fitsInRam = true,
            ),
        )
        // Settings come from DataStore on a real IO thread: wait for the first loaded state.
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf("chat"), state.chatModels.map { it.info.id })
        assertEquals(listOf("embed"), state.embeddingModels.map { it.info.id })
        assertEquals(8, state.deviceRamGb)
        assertEquals("chat", state.recommendedId)
    }

    @Test
    fun notEnoughStorageEmitsErrorWithNeededBytes() = runTest {
        repo.downloadResult = AppResult.Failure(ErrorKind.NOT_ENOUGH_STORAGE)

        viewModel.download("chat")

        assertEquals(ModelsEvent.Error(ErrorKind.NOT_ENOUGH_STORAGE, 1_200L), viewModel.events.first())
    }

    @Test
    fun pauseAndDownloadAreForwarded() = runTest {
        viewModel.download("chat")
        viewModel.pause("chat")
        advanceUntilIdle()
        assertEquals(listOf("chat"), repo.downloads)
        assertEquals(listOf("chat"), repo.paused)
    }

    @Test
    fun mobileDataToggleIsSaved() = runTest {
        viewModel.setAllowMobileData(true)
        // DataStore writes on its own IO thread: wait until the new value is observed.
        assertTrue(settings.settings.first { it.allowMobileData }.allowMobileData)
    }
}
