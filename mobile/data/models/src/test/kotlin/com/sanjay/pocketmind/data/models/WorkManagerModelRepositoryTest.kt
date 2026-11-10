package com.sanjay.pocketmind.data.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.LlmBackend
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.FakeProStatus
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.sanjay.pocketmind.data.models.work.ModelDownloadWorker
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The model repository with a REAL WorkManager in test mode. Robolectric has no network, so
 * the "Wi-Fi only" constraint is never met: an enqueued download stays ENQUEUED and we can
 * observe exactly what the repository asked WorkManager to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkManagerModelRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val content = "weights".toByteArray()
    private val chat = info("chat", ModelKind.CHAT, minRam = 4)
    private val big = info("big", ModelKind.CHAT, minRam = 16)
    private val embed = info("embed", ModelKind.EMBEDDING, minRam = 2)
    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())
    private val engine = FakeLlmEngine()
    private var freeBytes = Long.MAX_VALUE

    private lateinit var workManager: WorkManager
    private lateinit var files: ModelFiles
    private lateinit var guard: LoadGuard
    private lateinit var repository: WorkManagerModelRepository

    private fun info(id: String, kind: ModelKind, minRam: Int) = LlmModelInfo(
        id = id,
        displayName = id,
        description = "",
        kind = kind,
        url = "https://huggingface.co/$id",
        sizeBytes = content.size.toLong(),
        sha256 = Sha256.ofBytes(content),
        minRamGb = minRam,
        license = "Apache-2.0",
    )

    private val pro = FakeProStatus()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        files = ModelFiles.forTesting(tmp.newFolder("models"))
        guard = LoadGuard.forTesting(tmp.newFolder("guard")) { true }
        val catalog = mockk<ModelCatalog> {
            every { models } returns listOf(chat, big, embed)
            every { find(any()) } answers { listOf(chat, big, embed).firstOrNull { it.id == firstArg() } }
        }
        val deviceInfo = object : DeviceInfo {
            override fun totalRamGb() = 8
            override fun freeStorageBytes() = freeBytes
        }
        repository = WorkManagerModelRepository(
            workManager = workManager,
            catalog = catalog,
            files = files,
            deviceInfo = deviceInfo,
            settings = settings,
            engine = engine,
            verifier = ModelVerifier(catalog, files, guard, Dispatchers.Unconfined),
            // Not a TestDispatcher: the repository is built here, outside runTest's scheduler.
            io = Dispatchers.Unconfined,
            pro = pro,
        )
    }

    private fun install(model: LlmModelInfo, bytes: ByteArray = content) {
        files.finalFile(model.id).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    private fun jobs(modelId: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(ModelDownloadWorker.uniqueName(modelId)).get()

    private suspend fun status(id: String) = repository.observeModels().first().first { it.info.id == id }

    @Test
    fun `models show RAM fit, selection and download state`() = runTest {
        install(chat)
        settings.setSelectedModel(chat.id)

        val models = repository.observeModels().first()

        assertEquals(listOf("chat", "big", "embed"), models.map { it.info.id })
        assertEquals(ModelStatus.Downloaded, models[0].status)
        assertTrue(models[0].isSelected)
        assertFalse(models[1].fitsInRam) // 16 GB model on an 8 GB phone
        assertEquals(ModelStatus.NotDownloaded, models[2].status)
        assertEquals(8, repository.deviceRamGb())
    }

    @Test
    fun `download enqueues one unique job that waits for Wi-Fi`() = runTest {
        assertEquals(AppResult.Success(Unit), repository.download(chat.id))
        assertEquals(AppResult.Success(Unit), repository.download(chat.id)) // tapping twice

        val active = jobs(chat.id).filter { !it.state.isFinished }
        assertEquals(1, active.size)
        assertEquals(WorkInfo.State.ENQUEUED, active.single().state)
        assertTrue(ModelDownloadWorker.modelTag(chat.id) in active.single().tags)
        assertEquals(ModelStatus.Queued(0, chat.sizeBytes), status(chat.id).status)
    }

    @Test
    fun `download fails fast without storage and enqueues nothing`() = runTest {
        freeBytes = 0

        val result = repository.download(chat.id)

        assertEquals(ErrorKind.NOT_ENOUGH_STORAGE, (result as AppResult.Failure).kind)
        assertTrue(jobs(chat.id).isEmpty())
    }

    @Test
    fun `an unknown model can't be downloaded`() = runTest {
        assertEquals(ErrorKind.UNKNOWN, (repository.download("nope") as AppResult.Failure).kind)
    }

    @Test
    fun `pause cancels the job but keeps the partial file, cancel deletes it`() = runTest {
        repository.download(chat.id)
        files.partFile(chat.id).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))

        repository.pause(chat.id)
        assertTrue(jobs(chat.id).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(ModelStatus.Paused(3, chat.sizeBytes), status(chat.id).status)

        repository.cancel(chat.id)
        assertEquals(0L, files.partialBytes(chat.id))
        assertEquals(ModelStatus.NotDownloaded, status(chat.id).status)
    }

    @Test
    fun `delete unloads the loaded model, frees the file and falls back to another chat model`() = runTest {
        install(chat)
        install(big)
        settings.setSelectedModel(chat.id)
        engine.load(files.finalFile(chat.id).path, preferGpu = true)
        val path = files.finalFile(chat.id).path
        guard.begin(path, LlmBackend.GPU)
        LoadGuard.forTesting(tmp.root.resolve("guard")) { true }.recoverFromPreviousRun()

        repository.delete(chat.id)

        assertEquals(1, engine.unloadCount)
        assertEquals(EngineState.Idle, engine.state.value)
        assertFalse(files.isDownloaded(chat.id))
        assertEquals(big.id, settings.current().selectedModelId)
        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(path))
    }

    @Test
    fun `deleting the only chat model clears the selection`() = runTest {
        install(chat)
        settings.setSelectedModel(chat.id)

        repository.delete(chat.id)

        assertNull(settings.current().selectedModelId)
        assertEquals(0, engine.unloadCount) // it wasn't loaded
    }

    @Test
    fun `select accepts only downloaded chat models`() = runTest {
        repository.select(chat.id) // not downloaded
        assertNull(settings.current().selectedModelId)

        install(embed)
        repository.select(embed.id) // not a chat model
        assertNull(settings.current().selectedModelId)

        install(chat)
        repository.select(chat.id)
        assertEquals(chat.id, settings.current().selectedModelId)
    }

    @Test
    fun `verify deletes a damaged file`() = runTest {
        install(chat, bytes = "WEIGHTS".toByteArray()) // same size, different bytes
        settings.setSelectedModel(chat.id)

        val result = repository.verify(chat.id)

        assertEquals(ErrorKind.CHECKSUM_MISMATCH, (result as AppResult.Failure).kind)
        assertFalse(files.isDownloaded(chat.id))
        assertNull(settings.current().selectedModelId)
    }

    @Test
    fun `verify keeps an intact file`() = runTest {
        install(chat)

        assertEquals(AppResult.Success(Unit), repository.verify(chat.id))
        assertTrue(files.isDownloaded(chat.id))
    }

    @Test
    fun `required free bytes include the safety margin and subtract what is already downloaded`() {
        val fresh = repository.requiredFreeBytes(chat.id)
        files.partFile(chat.id).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2))

        assertTrue(fresh >= chat.sizeBytes)
        assertTrue(repository.requiredFreeBytes(chat.id) <= fresh)
        assertEquals(0L, repository.requiredFreeBytes("nope"))
    }

    @Test
    fun `a Pro-only model is refused without Pro and offered with it`() = runTest {
        val proModel = chat.copy(requiresPro = true)
        val catalog = mockk<ModelCatalog> {
            every { models } returns listOf(proModel)
            every { find(any()) } returns proModel
        }
        val gated = WorkManagerModelRepository(
            workManager = workManager,
            catalog = catalog,
            files = files,
            deviceInfo = object : DeviceInfo {
                override fun totalRamGb() = 8
                override fun freeStorageBytes() = Long.MAX_VALUE
            },
            settings = SettingsDataSource(InMemoryPreferencesDataStore()),
            engine = FakeLlmEngine(),
            verifier = ModelVerifier(catalog, files, guard, Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
            pro = pro,
        )

        assertEquals(ErrorKind.NEEDS_PRO, (gated.download(proModel.id) as AppResult.Failure).kind)
        pro.isPro.value = true
        assertTrue(gated.download(proModel.id) is AppResult.Success)
    }
}
