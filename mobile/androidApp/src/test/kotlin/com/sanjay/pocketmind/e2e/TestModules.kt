package com.sanjay.pocketmind.e2e

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.billing.di.BillingModule
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.database.di.DatabaseFileModule
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.datastore.di.DataStoreModule
import com.sanjay.pocketmind.core.llm.DefaultPrompts
import com.sanjay.pocketmind.core.llm.ExitReasonSource
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.llm.TextEmbedder
import com.sanjay.pocketmind.core.llm.di.LlmModule
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.FakeProStatus
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.sanjay.pocketmind.data.models.ModelRepository
import com.sanjay.pocketmind.data.models.ModelState
import com.sanjay.pocketmind.data.models.ModelStatus
import com.sanjay.pocketmind.data.models.di.ModelSelectionModule
import com.sanjay.pocketmind.play.PlayModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/*
 * Hilt test modules for the end-to-end tests. Each one replaces ONE production module whose real
 * version needs a phone (native LiteRT-LM, SQLCipher, Play services, a file-based DataStore that
 * can't replace files on Windows JVMs) or the network (model downloads). Everything else in the app
 * — every ViewModel, repository, DAO, screen and navigation — is the real code.
 */

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LlmModule::class])
object TestLlmModule {
    @Provides
    @Singleton
    fun providesFakeEngine(): FakeLlmEngine = FakeLlmEngine(listOf("Drones", " fly", " with", " four", " rotors."))

    @Provides
    fun providesEngine(engine: FakeLlmEngine): LlmEngine = engine

    @Provides
    fun providesEmbedder(): TextEmbedder = object : TextEmbedder {
        override suspend fun load(modelPath: String) = Unit

        override suspend fun embedDocuments(texts: List<String>) = texts.map { FloatArray(EMBEDDING_SIZE) { 1f } }

        override suspend fun embedQuery(text: String) = FloatArray(EMBEDDING_SIZE) { 1f }

        override suspend fun unload() = Unit
    }

    @Provides
    fun providesExitReasons(): ExitReasonSource = ExitReasonSource { false }

    private const val EMBEDDING_SIZE = 4
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseFileModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): PocketMindDatabase =
        Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun providesDataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [BillingModule::class])
object TestBillingModule {
    @Provides
    @Singleton
    fun providesProStatus(): ProStatus = FakeProStatus()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlayModule::class])
object TestPlayModule {
    @Provides
    @Singleton
    fun providesUpdates(@ApplicationContext context: Context): AppUpdateManager =
        FakeAppUpdateManager(context).apply { setUpdateNotAvailable() }

    @Provides
    @Singleton
    fun providesReviews(@ApplicationContext context: Context): ReviewManager = FakeReviewManager(context)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ModelSelectionModule::class])
object TestModelSelectionModule {
    @Provides
    fun providesModelRepository(models: FakeModels): ModelRepository = models

    @Provides
    fun providesInferenceSettings(models: FakeModels): InferenceSettingsSource = models
}

/** One small chat model whose "download" finishes at once; it answers through [FakeLlmEngine]. */
@Singleton
class FakeModels @Inject constructor(private val settings: SettingsDataSource) :
    ModelRepository,
    InferenceSettingsSource {
    val model = LlmModelInfo(
        id = "test-model",
        displayName = "Test Model",
        description = "A tiny model for tests.",
        kind = ModelKind.CHAT,
        url = "https://huggingface.co/test/model.litertlm",
        sizeBytes = 1_000_000,
        sha256 = "0".repeat(SHA256_HEX),
        minRamGb = 1,
        license = "Apache-2.0",
    )
    private val downloaded = MutableStateFlow(false)

    override fun observeModels(): Flow<List<ModelState>> = combine(downloaded, settings.settings) { done, user ->
        val status = if (done) ModelStatus.Downloaded else ModelStatus.NotDownloaded
        listOf(ModelState(model, status, isSelected = done && user.selectedModelId == model.id, fitsInRam = true))
    }

    override fun deviceRamGb() = RAM_GB

    override fun recommendedModelId() = model.id

    var downloadCalls = 0
        private set

    override suspend fun download(modelId: String): AppResult<Unit> {
        downloadCalls++
        downloaded.value = true
        settings.setSelectedModel(modelId)
        return AppResult.Success(Unit)
    }

    override fun pause(modelId: String) = Unit

    override suspend fun cancel(modelId: String) = Unit

    override suspend fun delete(modelId: String) {
        downloaded.value = false
    }

    override suspend fun verify(modelId: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun select(modelId: String) = settings.setSelectedModel(modelId)

    override fun requiredFreeBytes(modelId: String) = 0L

    override suspend fun current(): InferenceSettings? = if (downloaded.value) {
        InferenceSettings(
            modelPath = "/test/model.litertlm",
            preferGpu = true,
            systemPrompt = DefaultPrompts.SYSTEM,
            sampling = SamplingParams(),
            modelId = model.id,
            modelName = model.displayName,
        )
    } else {
        null
    }

    private companion object {
        const val SHA256_HEX = 64
        const val RAM_GB = 8
    }
}
