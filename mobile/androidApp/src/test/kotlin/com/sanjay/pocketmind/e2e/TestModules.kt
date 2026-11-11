package com.sanjay.pocketmind.e2e

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.DefaultPrompts
import com.sanjay.pocketmind.core.llm.ExitReasonSource
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.llm.TextEmbedder
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.FakeProStatus
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.sanjay.pocketmind.data.models.ModelRepository
import com.sanjay.pocketmind.data.models.ModelState
import com.sanjay.pocketmind.data.models.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/*
 * Koin test definitions for the end-to-end tests (V3; v2 used Hilt @TestInstallIn). They come AFTER the
 * production modules, so each one overrides ONE production definition whose real version needs a phone
 * (native LiteRT-LM, SQLCipher, Play services, a file-based DataStore that can't replace files on Windows
 * JVMs) or the network (model downloads). Everything else in the app — every ViewModel, repository, DAO,
 * screen and navigation — is the real code.
 */
val endToEndTestModule: Module = module {
    // Replaces llmModule: a scripted engine, a constant embedder, no crash history.
    single { FakeLlmEngine(listOf("Drones", " fly", " with", " four", " rotors.")) } bind LlmEngine::class
    single<TextEmbedder> {
        object : TextEmbedder {
            override suspend fun load(modelPath: String) = Unit

            override suspend fun embedDocuments(texts: List<String>) = texts.map { FloatArray(EMBEDDING_SIZE) { 1f } }

            override suspend fun embedQuery(text: String) = FloatArray(EMBEDDING_SIZE) { 1f }

            override suspend fun unload() = Unit
        }
    }
    factory<ExitReasonSource> { ExitReasonSource { false } }

    // Replaces databaseFileModule: Room in memory (no SQLCipher native code on the JVM).
    single<PocketMindDatabase> {
        Room.inMemoryDatabaseBuilder(androidContext(), PocketMindDatabase::class.java).allowMainThreadQueries().build()
    }

    // Replaces dataStoreModule: settings in memory.
    single<DataStore<Preferences>> { InMemoryPreferencesDataStore() }

    // Replaces billingModule.
    single<ProStatus> { FakeProStatus() }

    // Replaces playModule: Play's own fakes (no Play Store on the JVM).
    single<AppUpdateManager> { FakeAppUpdateManager(androidContext()).apply { setUpdateNotAvailable() } }
    single<ReviewManager> { FakeReviewManager(androidContext()) }

    // Replaces modelSelectionModule: one installed fake model.
    single { FakeModels(get()) } binds arrayOf(ModelRepository::class, InferenceSettingsSource::class)
}

private const val EMBEDDING_SIZE = 4

/** One small chat model whose "download" finishes at once; it answers through [FakeLlmEngine]. */
class FakeModels(private val settings: SettingsDataSource) :
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
