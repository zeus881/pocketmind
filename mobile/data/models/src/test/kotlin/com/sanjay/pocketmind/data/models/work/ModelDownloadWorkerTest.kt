package com.sanjay.pocketmind.data.models.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.LlmBackend
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import com.sanjay.pocketmind.data.models.DeviceInfo
import com.sanjay.pocketmind.data.models.ModelCatalog
import com.sanjay.pocketmind.data.models.Sha256
import com.sanjay.pocketmind.data.models.download.ModelDownloader
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
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
 * The download worker end to end, minus the internet: a real local HTTP server
 * (MockWebServer), real files in a temp folder, real WorkManager test machinery.
 * Robolectric supplies the Android Context, NotificationManager and WorkManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ModelDownloadWorkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val data = Random(7).nextBytes(200_000)
    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())
    private lateinit var files: ModelFiles
    private lateinit var guard: LoadGuard
    private var freeBytes = Long.MAX_VALUE

    private val deviceInfo = object : DeviceInfo {
        override fun totalRamGb() = 8
        override fun freeStorageBytes() = freeBytes
    }

    @Before
    fun setUp() {
        // The worker builds a "Pause" PendingIntent through WorkManager, so it must exist.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server.start()
        files = ModelFiles.forTesting(tmp.newFolder("models"))
        guard = LoadGuard.forTesting(tmp.newFolder("guard")) { true }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun model(id: String = "chat", kind: ModelKind = ModelKind.CHAT, sha: String = Sha256.ofBytes(data)) =
        LlmModelInfo(
            id = id,
            displayName = id,
            description = "",
            kind = kind,
            url = server.url("/$id.litertlm").toString(),
            sizeBytes = data.size.toLong(),
            sha256 = sha,
            minRamGb = 1,
            license = "Apache-2.0",
        )

    private fun worker(catalogModel: LlmModelInfo?, inputId: String? = catalogModel?.id, runAttempt: Int = 0) =
        TestListenableWorkerBuilder<ModelDownloadWorker>(context)
            .setInputData(inputId?.let { workDataOf(ModelDownloadWorker.KEY_MODEL_ID to it) } ?: Data.EMPTY)
            .setRunAttemptCount(runAttempt)
            .setWorkerFactory(factory(catalogModel))
            .build()

    private fun factory(catalogModel: LlmModelInfo?) = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            ModelDownloadWorker(
                appContext,
                workerParameters,
                catalog = mockk<ModelCatalog> {
                    every { find(any()) } returns null
                    if (catalogModel != null) every { find(catalogModel.id) } returns catalogModel
                },
                downloader = ModelDownloader(OkHttpClient(), files, UnconfinedTestDispatcher()),
                deviceInfo = deviceInfo,
                files = files,
                settings = settings,
                loadGuard = guard,
            )
    }

    private fun failure(kind: ErrorKind) =
        ListenableWorker.Result.failure(workDataOf(ModelDownloadWorker.KEY_ERROR to kind.name))

    @Test
    fun `a successful download verifies the file, selects the first chat model and clears crash records`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(data)))
        val model = model()
        val finalPath = files.finalFile(model.id).path
        guard.begin(finalPath, LlmBackend.GPU)
        LoadGuard.forTesting(tmp.root.resolve("guard")) { true }.recoverFromPreviousRun()

        val result = worker(model).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(files.isDownloaded(model.id))
        assertEquals(model.id, settings.current().selectedModelId)
        assertEquals(emptySet<LlmBackend>(), guard.blockedBackends(finalPath))
    }

    @Test
    fun `an embedding model never becomes the selected chat model`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(data)))

        val result = worker(model(id = "embed", kind = ModelKind.EMBEDDING)).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(settings.current().selectedModelId)
    }

    @Test
    fun `an existing selection is kept`() = runTest {
        settings.setSelectedModel("other")
        server.enqueue(MockResponse().setBody(Buffer().write(data)))

        worker(model()).doWork()

        assertEquals("other", settings.current().selectedModelId)
    }

    @Test
    fun `not enough storage fails before any byte is downloaded`() = runTest {
        freeBytes = 0

        val result = worker(model()).doWork()

        assertEquals(failure(ErrorKind.NOT_ENOUGH_STORAGE), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a network error is retried while attempts remain`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ListenableWorker.Result.retry(), worker(model(), runAttempt = 0).doWork())
    }

    @Test
    fun `a network error after the last attempt fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(failure(ErrorKind.NETWORK), worker(model(), runAttempt = 5).doWork())
    }

    @Test
    fun `a checksum mismatch fails and keeps no file`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(data)))
        val model = model(sha = "0".repeat(64))

        val result = worker(model).doWork()

        assertEquals(failure(ErrorKind.CHECKSUM_MISMATCH), result)
        assertFalse(files.isDownloaded(model.id))
        assertEquals(0L, files.partialBytes(model.id))
    }

    @Test
    fun `an unknown or missing model id fails as UNKNOWN`() = runTest {
        assertEquals(failure(ErrorKind.UNKNOWN), worker(catalogModel = null, inputId = "nope").doWork())
        assertEquals(failure(ErrorKind.UNKNOWN), worker(catalogModel = null, inputId = null).doWork())
    }

    @Test
    fun `foreground info describes the download`() = runTest {
        val info = worker(model()).getForegroundInfo()

        assertEquals("chat".hashCode(), info.notificationId)
    }
}
