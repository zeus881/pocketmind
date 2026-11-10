package com.sanjay.pocketmind.data.documents.rag

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.database.entity.ChunkEntity
import com.sanjay.pocketmind.core.llm.EmbeddingModelSource
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.FakeTextEmbedder
import com.sanjay.pocketmind.data.documents.ExtractedDocument
import com.sanjay.pocketmind.data.documents.PageText
import com.sanjay.pocketmind.data.documents.PdfTextExtractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class VectorMathTest {
    @Test
    fun cosineOfSameDirectionIsOneAndOfOrthogonalIsZero() {
        assertEquals(1f, VectorMath.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f)), 1e-6f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(-1f, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(-3f, 0f)), 1e-6f)
    }

    @Test
    fun zeroVectorHasZeroSimilarity() {
        assertEquals(0f, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0f)
    }

    @Test
    fun bytesRoundTripExactly() {
        val v = floatArrayOf(0.1f, -2.5f, 3.75f, Float.MIN_VALUE)
        val bytes = VectorMath.toBytes(v)
        assertEquals(16, bytes.size)
        assertArrayEquals(v, VectorMath.fromBytes(bytes), 0f)
    }
}

class VectorStoreTest {
    private fun chunk(i: Int, page: Int, vector: FloatArray) = ChunkEntity(
        documentId = "d",
        chunkIndex = i,
        startPage = page,
        endPage = page,
        text = "c$i",
        embedding = VectorMath.toBytes(vector),
    )

    @Test
    fun returnsTheKMostSimilarInOrder() {
        val chunks = listOf(
            chunk(0, 1, floatArrayOf(1f, 0f)),
            chunk(1, 2, floatArrayOf(0.9f, 0.1f)),
            chunk(2, 3, floatArrayOf(0f, 1f)),
            chunk(3, 4, floatArrayOf(-1f, 0f)),
        )
        val top = VectorStore.topK(floatArrayOf(1f, 0f), chunks, k = 2)
        assertEquals(listOf(0, 1), top.map { it.chunkIndex })
        assertTrue(top[0].score >= top[1].score)
    }
}

class GroundedPromptTest {
    private val sources = listOf(
        SourceChunk(1, startPage = 7, endPage = 7, text = "Batteries last 30 minutes.", score = 0.9f),
        SourceChunk(0, startPage = 3, endPage = 4, text = "The drone weighs 249 g.", score = 0.8f),
    )

    @Test
    fun promptHasRulesCitedContextInPageOrderAndQuestion() {
        val prompt = GroundedPrompt.build("How heavy is it?", sources)
        assertTrue(prompt.contains("ONLY the context"))
        assertTrue(prompt.contains(GroundedPrompt.I_DONT_KNOW))
        assertTrue(prompt.contains("[p. 7]"))
        assertTrue(prompt.indexOf("[p. 3-4] The drone weighs") < prompt.indexOf("[p. 7] Batteries"))
        assertTrue(prompt.endsWith("Question: How heavy is it?"))
    }

    @Test
    fun citationsAreParsedIncludingRanges() {
        val answer = "It weighs 249 g [p. 3-4]. Flight time is 30 min [p.7]. Again [p. 7]."
        assertEquals(listOf(3, 4, 7), Citations.pages(answer))
    }

    @Test
    fun noCitationsGivesEmptyList() {
        assertEquals(emptyList<Int>(), Citations.pages(GroundedPrompt.I_DONT_KNOW))
    }
}

/** End-to-end RAG with real Room (in memory), fake embedder and fake LLM. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryTest {
    private lateinit var db: PocketMindDatabase
    private val engine = FakeLlmEngine(responsePieces = listOf("It flies 30 minutes [p. 2]."))
    private val embedder = FakeTextEmbedder()
    private var embeddingPath: String? = "/embed.litertlm"
    private val pages = listOf(
        PageText(1, "The quadcopter frame is made of carbon fibre and weighs 249 grams."),
        PageText(2, "The lithium polymer battery gives a flight time of thirty minutes."),
        PageText(3, "Regulations require registration of every drone above 250 grams."),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun repository() = DocumentRepository(
        extractor = object : PdfTextExtractor {
            override suspend fun extract(uri: Uri) = AppResult.Success(ExtractedDocument("drone.pdf", pages))
        },
        embedder = embedder,
        embeddingModel = object : EmbeddingModelSource {
            override suspend fun embeddingModelPath() = embeddingPath
            override fun embeddingModelId() = "embeddinggemma"
        },
        task = LlmTask(
            engine,
            object : InferenceSettingsSource {
                override suspend fun current() = InferenceSettings("/m", true, "S", SamplingParams())
            },
        ),
        dao = db.documentDao(),
        clock = { 0L },
        cpu = UnconfinedTestDispatcher(),
    )

    private val uri = Uri.parse("content://docs/drone.pdf")

    @Test
    fun indexingStoresChunksWithVectorsAndReportsProgress() = runTest {
        val progress = repository().index(uri).toList()

        val done = progress.last() as IndexProgress.Done
        assertEquals("drone.pdf", done.document.name)
        assertEquals(3, done.document.pageCount)
        assertEquals(3, done.document.chunkCount) // one chunk per (short) page, never across pages
        assertTrue(progress.any { it is IndexProgress.Embedding })
        val stored = db.documentDao().chunks(done.document.id)
        assertEquals(done.document.chunkCount, stored.size)
        assertEquals(FakeTextEmbedder.DIMENSIONS * 4, stored.first().embedding.size)
        assertEquals("/embed.litertlm", embedder.loadedPath)
    }

    @Test
    fun askRetrievesTheRelevantPageAndGroundsTheAnswer() = runTest {
        val repo = repository()
        val doc = (repo.index(uri).toList().last() as IndexProgress.Done).document

        val answer = (repo.ask(doc.id, "What flight time does the battery give?") as AppResult.Success).value

        assertEquals(2, answer.sources.first().startPage) // the battery page ranks first
        assertEquals("It flies 30 minutes [p. 2].", answer.text.toList().joinToString(""))
        val prompt = engine.prompts.single()
        assertTrue(prompt.contains("[p. 2] The lithium polymer battery"))
        assertTrue(prompt.contains("Question: What flight time does the battery give?"))
        assertTrue(engine.sessions.single().history.isEmpty()) // fresh session, no chat leakage
    }

    @Test
    fun missingEmbeddingModelFailsClearly() = runTest {
        embeddingPath = null
        assertEquals(IndexProgress.Failed(ErrorKind.NO_MODEL), repository().index(uri).first())
    }

    @Test
    fun deletingADocumentRemovesItsChunks() = runTest {
        val repo = repository()
        val doc = (repo.index(uri).toList().last() as IndexProgress.Done).document
        repo.delete(doc.id)
        assertTrue(db.documentDao().chunks(doc.id).isEmpty())
        assertTrue(repo.observeDocuments().first().isEmpty())
    }
}
