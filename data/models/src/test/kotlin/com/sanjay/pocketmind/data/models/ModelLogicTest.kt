package com.sanjay.pocketmind.data.models

import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.data.models.download.HostAllowlist
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256Test {
    @Test
    fun knownTestVector() {
        // Official NIST test vector for "abc".
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.ofBytes("abc".toByteArray()),
        )
    }

    @Test
    fun matchesIgnoresCase() {
        assertTrue(Sha256.matches("ABCDEF", "abcdef"))
        assertFalse(Sha256.matches("abcdef", "abcdee"))
    }
}

class ModelCatalogTest {
    // Gradle runs unit tests with the module folder as working directory.
    private val models = ModelCatalog.parse(File("src/main/assets/models.json").readText())

    @Test
    fun catalogIsValid() {
        assertTrue(models.isNotEmpty())
        assertEquals("ids must be unique", models.size, models.map { it.id }.toSet().size)
        models.forEach { m ->
            assertTrue("${m.id}: sha256 must be 64 hex chars", m.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue("${m.id}: must be a positive size", m.sizeBytes > 0)
            // Pinned to a 40-char commit hash, never a moving branch like "main".
            val pinned = Regex("https://huggingface\\.co/.+/resolve/[0-9a-f]{40}/.+")
            assertTrue("${m.id}: url must be pinned", m.url.matches(pinned))
            assertTrue("${m.id}: url must be allowed", HostAllowlist.isAllowed(m.url.toHttpUrl()))
        }
    }

    @Test
    fun hasExactlyOneEmbeddingModelAndSeveralChatModels() {
        assertEquals(1, models.count { it.kind == ModelKind.EMBEDDING })
        assertTrue(models.count { it.kind == ModelKind.CHAT } >= 2)
    }
}

class ModelRecommenderTest {
    private fun model(id: String, size: Long, ram: Int, kind: ModelKind = ModelKind.CHAT) =
        LlmModelInfo(id, id, "", kind, "https://huggingface.co/x", size, "0".repeat(64), ram, "Apache-2.0")

    private val catalog = listOf(
        model("big", 2_600, ram = 8),
        model("mid", 960, ram = 6),
        model("small", 610, ram = 4),
        model("embed", 160, ram = 4, kind = ModelKind.EMBEDDING),
    )

    @Test
    fun recommendsTheLargestModelThatFits() {
        assertEquals("big", ModelRecommender.recommend(catalog, 12)?.id)
        assertEquals("big", ModelRecommender.recommend(catalog, 8)?.id)
        assertEquals("mid", ModelRecommender.recommend(catalog, 6)?.id)
        assertEquals("small", ModelRecommender.recommend(catalog, 4)?.id)
    }

    @Test
    fun tooLittleRamStillGetsTheSmallestChatModel() {
        assertEquals("small", ModelRecommender.recommend(catalog, 2)?.id)
    }

    @Test
    fun storageNeedsTwentyPercentHeadroomMinusWhatIsAlreadyDownloaded() {
        assertEquals(1_200L, ModelRecommender.requiredFreeBytes(model("m", 1_000, 4)))
        assertEquals(480L, ModelRecommender.requiredFreeBytes(model("m", 1_000, 4), alreadyDownloaded = 600))
    }
}

class ModelStatusResolverTest {
    private val model =
        LlmModelInfo("m", "M", "", ModelKind.CHAT, "https://huggingface.co/x", 1_000, "0".repeat(64), 4, "")

    private fun resolve(
        phase: DownloadWork.Phase,
        done: Long = 0,
        failure: ErrorKind? = null,
        final: Boolean = false,
        part: Long = 0,
    ) = ModelStatusResolver.resolve(model, DownloadWork(phase, done, failure), final, part)

    @Test
    fun finalFileAlwaysWins() {
        assertEquals(ModelStatus.Downloaded, resolve(DownloadWork.Phase.FAILED, final = true))
    }

    @Test
    fun runningShowsProgress() {
        assertEquals(ModelStatus.Downloading(500, 1_000), resolve(DownloadWork.Phase.RUNNING, done = 500, part = 400))
    }

    @Test
    fun cancelledWithPartIsPaused_withoutPartIsNotDownloaded() {
        assertEquals(ModelStatus.Paused(300, 1_000), resolve(DownloadWork.Phase.CANCELLED, part = 300))
        assertEquals(ModelStatus.NotDownloaded, resolve(DownloadWork.Phase.CANCELLED))
    }

    @Test
    fun failureCarriesTheReason() {
        assertEquals(
            ModelStatus.Failed(ErrorKind.NOT_ENOUGH_STORAGE, 0, 1_000),
            resolve(DownloadWork.Phase.FAILED, failure = ErrorKind.NOT_ENOUGH_STORAGE),
        )
    }

    @Test
    fun enqueuedIsQueued() {
        assertEquals(ModelStatus.Queued(0, 1_000), resolve(DownloadWork.Phase.ENQUEUED))
    }
}

class HostAllowlistTest {
    @Test
    fun onlyHuggingFaceOverHttps() {
        assertTrue(HostAllowlist.isAllowed("https://huggingface.co/a/b".toHttpUrl()))
        assertTrue(HostAllowlist.isAllowed("https://cas-bridge.xethub.hf.co/x".toHttpUrl()))
        assertTrue(HostAllowlist.isAllowed("https://cdn-lfs.huggingface.co/x".toHttpUrl()))
        assertFalse(HostAllowlist.isAllowed("http://huggingface.co/a".toHttpUrl()))
        assertFalse(HostAllowlist.isAllowed("https://evil.com/huggingface.co".toHttpUrl()))
        assertFalse(HostAllowlist.isAllowed("https://huggingface.co.evil.com/x".toHttpUrl()))
        assertFalse(HostAllowlist.isAllowed("https://analytics.google.com/x".toHttpUrl()))
    }
}
