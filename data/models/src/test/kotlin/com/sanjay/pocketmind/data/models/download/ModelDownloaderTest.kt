package com.sanjay.pocketmind.data.models.download

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.data.models.Sha256
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Downloader tests against a REAL local HTTP server (MockWebServer): real sockets, real
 * headers, real partial responses — but no internet and fully deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val data = Random(42).nextBytes(300_000)
    private lateinit var files: ModelFiles

    @Before
    fun setUp() {
        server.start()
        files = ModelFiles.forTesting(tmp.newFolder("models"))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun model(sha: String = Sha256.ofBytes(data)) = LlmModelInfo(
        id = "test-model",
        displayName = "Test",
        description = "",
        url = server.url("/model.litertlm").toString(),
        sizeBytes = data.size.toLong(),
        sha256 = sha,
        minRamGb = 1,
        license = "Apache-2.0",
    )

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    private suspend fun download(model: LlmModelInfo, progress: MutableList<Long> = mutableListOf()) =
        ModelDownloader(OkHttpClient(), files, UnconfinedTestDispatcher())
            .download(model) { done, _ -> progress += done }

    @Test
    fun freshDownloadVerifiesAndRenamesAtomically() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(data)))
        val progress = mutableListOf<Long>()

        val result = download(model(), progress)

        assertTrue(result is AppResult.Success)
        assertArrayEquals(data, files.finalFile("test-model").readBytes())
        assertFalse(files.partFile("test-model").exists())
        assertEquals(data.size.toLong(), progress.last())
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun resumeSendsRangeHeaderAndAppends() = runTest {
        val half = data.size / 2
        files.partFile("test-model").apply { parentFile!!.mkdirs() }.writeBytes(data.copyOfRange(0, half))
        server.enqueue(MockResponse().setResponseCode(206).setBody(body(data.copyOfRange(half, data.size))))

        val result = download(model())

        assertTrue(result is AppResult.Success)
        assertEquals("bytes=$half-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(data, files.finalFile("test-model").readBytes())
    }

    @Test
    fun serverIgnoringRangeRestartsFromZero() = runTest {
        files.partFile("test-model").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(1000) { 7 })
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(data)))

        val result = download(model())

        assertTrue(result is AppResult.Success)
        assertArrayEquals(data, files.finalFile("test-model").readBytes())
    }

    @Test
    fun checksumMismatchDeletesThePartialFile() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(data)))

        val result = download(model(sha = "0".repeat(64)))

        assertEquals(ErrorKind.CHECKSUM_MISMATCH, (result as AppResult.Failure).kind)
        assertFalse(files.partFile("test-model").exists())
        assertFalse(files.isDownloaded("test-model"))
    }

    @Test
    fun interruptedDownloadKeepsPartForResume() = runTest {
        val first = data.copyOfRange(0, 100_000)
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(first)))

        val result = download(model())

        assertEquals(ErrorKind.NETWORK, (result as AppResult.Failure).kind)
        assertEquals(first.size.toLong(), files.partialBytes("test-model"))
    }

    @Test
    fun httpErrorIsANetworkFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = download(model())

        assertEquals(ErrorKind.NETWORK, (result as AppResult.Failure).kind)
    }
}
