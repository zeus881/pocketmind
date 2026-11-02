package com.sanjay.pocketmind.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.database.entity.ChunkEntity
import com.sanjay.pocketmind.core.database.entity.DocumentEntity
import com.sanjay.pocketmind.core.database.entity.asEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.model.BenchmarkPromptResult
import com.sanjay.pocketmind.core.model.BenchmarkResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What goes into Room comes back unchanged: the benchmark's prompt list (stored as JSON by a
 * TypeConverter) and a RAG chunk's embedding (stored as a BLOB).
 */
@RunWith(RobolectricTestRunner::class)
class EntityRoundTripTest {
    private lateinit var db: PocketMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun benchmarkRunsKeepTheirPromptResults() = runTest {
        val run = BenchmarkResult(
            id = "r1", createdAt = 5, modelId = "gemma", modelName = "Gemma 4 E2B", backend = "GPU", loadMs = 5_454,
            prompts = listOf(
                BenchmarkPromptResult("Short", 390, 26.3, 128, 5_300, fromEngine = true),
                BenchmarkPromptResult("Long", 410, 25.9, 512, 20_100, fromEngine = false),
            ),
            peakMemoryMb = 960, device = "iQOO I2221", androidVersion = "16",
        )

        db.benchmarkDao().insert(run.asEntity())

        assertEquals(listOf(run), db.benchmarkDao().observeAll().first().map { it.asModel() })
    }

    @Test
    fun chunksKeepTheirEmbeddingBytes() = runTest {
        val docs = db.documentDao()
        val embedding = byteArrayOf(1, 2, 3, 4, -1)
        docs.insertIndexed(
            DocumentEntity("d1", "Manual.pdf", 30, 1, 1, "embed", 2),
            listOf(
                ChunkEntity(
                    documentId = "d1",
                    chunkIndex = 0,
                    startPage = 3,
                    endPage = 4,
                    text = "Calibrate",
                    embedding = embedding,
                ),
            ),
        )

        val stored = docs.chunks("d1").single()
        assertArrayEquals(embedding, stored.embedding)
        assertEquals("Calibrate", stored.text)
    }

    @Test
    fun chunkEqualityComparesEmbeddingContent() {
        val a =
            ChunkEntity(
                id = 1,
                documentId = "d",
                chunkIndex = 0,
                startPage = 1,
                endPage = 1,
                text = "t",
                embedding = byteArrayOf(1, 2),
            )
        val sameBytes = a.copy(embedding = byteArrayOf(1, 2))
        val otherBytes = a.copy(embedding = byteArrayOf(9))

        assertEquals(a, sameBytes)
        assertEquals(a.hashCode(), sameBytes.hashCode())
        assertNotEquals(a, otherBytes)
    }
}
