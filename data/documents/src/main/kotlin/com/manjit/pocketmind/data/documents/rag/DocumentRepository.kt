package com.manjit.pocketmind.data.documents.rag

import android.net.Uri
import com.manjit.pocketmind.core.common.dispatchers.Dispatcher
import com.manjit.pocketmind.core.common.dispatchers.PmDispatchers
import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.core.common.time.Clock
import com.manjit.pocketmind.core.database.dao.DocumentDao
import com.manjit.pocketmind.core.database.entity.ChunkEntity
import com.manjit.pocketmind.core.database.entity.DocumentEntity
import com.manjit.pocketmind.core.llm.EmbeddingModelSource
import com.manjit.pocketmind.core.llm.LlmTask
import com.manjit.pocketmind.core.llm.SamplingParams
import com.manjit.pocketmind.core.llm.TextEmbedder
import com.manjit.pocketmind.data.documents.PdfTextExtractor
import com.manjit.pocketmind.data.documents.TextChunker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** An indexed PDF, ready for questions. */
data class IndexedDocument(val id: String, val name: String, val pageCount: Int, val chunkCount: Int)

sealed interface IndexProgress {
    data object ReadingPdf : IndexProgress

    data class Embedding(val done: Int, val total: Int) : IndexProgress

    data class Done(val document: IndexedDocument) : IndexProgress

    data class Failed(val kind: ErrorKind) : IndexProgress
}

/** The retrieved passages (shown as source chips) and the streamed, grounded answer. */
class Answer(val sources: List<SourceChunk>, val text: Flow<String>)

/**
 * "Ask your PDF" — retrieval-augmented generation (RAG), fully on device.
 * ```
 * INDEX (once per PDF):  PDF ─▶ pages ─▶ ~300-token chunks (overlap 50) ─▶ embedder ─▶ vectors ─▶ Room
 * ASK   (per question):  question ─▶ embedder ─▶ cosine vs. all chunk vectors ─▶ top 4 chunks
 *                        ─▶ grounded prompt "[p. N] chunk…" + rules ─▶ chat model ─▶ answer with [p. N]
 * ```
 */
@Singleton
class DocumentRepository @Inject constructor(
    private val extractor: PdfTextExtractor,
    private val embedder: TextEmbedder,
    private val embeddingModel: EmbeddingModelSource,
    private val task: LlmTask,
    private val dao: DocumentDao,
    private val clock: Clock,
    @Dispatcher(PmDispatchers.Default) private val cpu: CoroutineDispatcher,
) {
    fun observeDocuments(): Flow<List<IndexedDocument>> = dao.observeAll().map { list ->
        list.map { IndexedDocument(it.id, it.name, it.pageCount, it.chunkCount) }
    }

    suspend fun isEmbeddingModelReady(): Boolean = embeddingModel.embeddingModelPath() != null

    fun index(uri: Uri): Flow<IndexProgress> = flow {
        val modelPath = embeddingModel.embeddingModelPath()
        val modelId = embeddingModel.embeddingModelId()
        if (modelPath == null || modelId == null) {
            emit(IndexProgress.Failed(ErrorKind.NO_MODEL))
            return@flow
        }
        emit(IndexProgress.ReadingPdf)
        val document = when (val extracted = extractor.extract(uri)) {
            is AppResult.Failure -> {
                emit(IndexProgress.Failed(extracted.kind))
                return@flow
            }

            is AppResult.Success -> extracted.value
        }
        // Chunk PAGE BY PAGE (never across pages) so every passage has one exact page number
        // to cite. Summaries can mix pages; citations can't.
        val chunks = document.pages
            .flatMap { page -> TextChunker.chunk(listOf(page), CHUNK_TOKENS, CHUNK_OVERLAP_TOKENS) }
            .mapIndexed { i, chunk -> chunk.copy(index = i) }
        if (chunks.isEmpty()) {
            emit(IndexProgress.Failed(ErrorKind.NO_TEXT_IN_DOCUMENT))
            return@flow
        }
        embedder.load(modelPath)

        val vectors = mutableListOf<FloatArray>()
        for (batch in chunks.chunked(PROGRESS_BATCH)) {
            emit(IndexProgress.Embedding(vectors.size, chunks.size))
            vectors += embedder.embedDocuments(batch.map { it.text })
        }
        val id = UUID.randomUUID().toString()
        val entity = DocumentEntity(
            id = id,
            name = document.name,
            pageCount = document.pageCount,
            chunkCount = chunks.size,
            createdAt = clock.nowMillis(),
            embeddingModelId = modelId,
            dimensions = vectors.first().size,
        )
        dao.insertIndexed(
            entity,
            chunks.mapIndexed { i, chunk ->
                ChunkEntity(
                    documentId = id,
                    chunkIndex = chunk.index,
                    startPage = chunk.startPage,
                    endPage = chunk.endPage,
                    text = chunk.text,
                    embedding = VectorMath.toBytes(vectors[i]),
                )
            },
        )
        emit(IndexProgress.Done(IndexedDocument(id, document.name, document.pageCount, chunks.size)))
    }

    /** Retrieves the best passages and returns a streamed grounded answer. */
    suspend fun ask(documentId: String, question: String): AppResult<Answer> {
        val modelPath = embeddingModel.embeddingModelPath() ?: return AppResult.Failure(ErrorKind.NO_MODEL)
        return when (val loaded = task.ensureLoaded()) {
            is AppResult.Failure -> loaded

            is AppResult.Success -> {
                val sources = retrieve(modelPath, documentId, question)
                if (sources.isEmpty()) {
                    AppResult.Failure(ErrorKind.NO_TEXT_IN_DOCUMENT)
                } else {
                    val prompt = GroundedPrompt.build(question, sources)
                    val answer = task.stream(GroundedPrompt.SYSTEM, prompt, SamplingParams.Precise)
                    AppResult.Success(Answer(sources, answer))
                }
            }
        }
    }

    /** Embeds the question and returns the [TOP_K] most similar chunks of the document. */
    private suspend fun retrieve(modelPath: String, documentId: String, question: String): List<SourceChunk> {
        embedder.load(modelPath)
        val queryVector = embedder.embedQuery(question)
        // Brute-force similarity over all chunks: CPU work, so on the Default dispatcher.
        return withContext(cpu) { VectorStore.topK(queryVector, dao.chunks(documentId), TOP_K) }
    }

    suspend fun delete(documentId: String) = dao.delete(documentId)

    companion object {
        /** ~300 tokens: small enough to be specific, big enough to hold a complete thought. */
        const val CHUNK_TOKENS = 300
        const val CHUNK_OVERLAP_TOKENS = 50
        const val TOP_K = 4
        private const val PROGRESS_BATCH = 8
    }
}
