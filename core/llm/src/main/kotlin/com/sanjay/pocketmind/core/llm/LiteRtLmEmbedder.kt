package com.sanjay.pocketmind.core.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.InputData
import com.sanjay.pocketmind.core.common.dispatchers.Dispatcher
import com.sanjay.pocketmind.core.common.dispatchers.PmDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [TextEmbedder] on LiteRT-LM's `EmbeddingEngine` (verified in the 0.19.0 AAR with javap).
 *
 * - CPU backend: the embedding model is small; running it on CPU leaves the GPU to the chat
 *   model, and CPU works on every phone.
 * - Texts are embedded in small batches (one native call per batch) to amortize call overhead
 *   while keeping memory bounded.
 * - `normalize = true` returns unit-length vectors, so cosine similarity = dot product.
 */
@OptIn(ExperimentalApi::class)
@Singleton
class LiteRtLmEmbedder @Inject constructor(
    @ApplicationContext context: Context,
    @Dispatcher(PmDispatchers.IO) private val io: CoroutineDispatcher,
) : TextEmbedder {

    private val cacheDir = context.cacheDir.absolutePath
    private val lock = Mutex()
    private var engine: EmbeddingEngine? = null
    private var loadedPath: String? = null

    override suspend fun load(modelPath: String) = withContext(io) {
        lock.withLock {
            if (engine != null && loadedPath == modelPath) return@withLock
            closeLocked()
            val created = EmbeddingEngine(
                EmbeddingEngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir),
            )
            created.initialize()
            engine = created
            loadedPath = modelPath
        }
    }

    override suspend fun embedDocuments(texts: List<String>): List<FloatArray> =
        embed(texts.map(EmbeddingPrompts::document))

    override suspend fun embedQuery(text: String): FloatArray = embed(listOf(EmbeddingPrompts.query(text))).single()

    private suspend fun embed(texts: List<String>): List<FloatArray> = withContext(io) {
        lock.withLock {
            val loaded = checkNotNull(engine) { "load() must be called before embedding" }
            val options = EmbeddingOptions(normalize = true)
            texts.chunked(BATCH_SIZE).flatMap { batch ->
                ensureActive() // indexing a long PDF can be cancelled between batches
                loaded.computeEmbeddingBatch(batch.map { listOf(InputData.Text(it)) }, options)
                    .map { it.embedding }
            }
        }
    }

    override suspend fun unload() = withContext(io) { lock.withLock { closeLocked() } }

    private fun closeLocked() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    private companion object {
        const val BATCH_SIZE = 8
    }
}
