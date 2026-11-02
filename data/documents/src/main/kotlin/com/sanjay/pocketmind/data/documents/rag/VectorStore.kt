package com.sanjay.pocketmind.data.documents.rag

import com.sanjay.pocketmind.core.database.entity.ChunkEntity

/** A retrieved passage with its page range and similarity score. */
data class SourceChunk(val chunkIndex: Int, val startPage: Int, val endPage: Int, val text: String, val score: Float)

/**
 * Nearest-neighbour search by BRUTE FORCE: compare the question vector with every chunk vector
 * of the document and keep the [k] most similar.
 *
 * Cost: O(n · d) — n chunks × d dimensions. A 100-page PDF has ~300 chunks × 768 dims ≈ 230,000
 * multiply-adds: well under 10 ms on a phone. A vector index (HNSW, e.g. ObjectBox) only pays off
 * above tens of thousands of chunks, and brings approximation errors and more complexity.
 * Documented as a known trade-off in the README.
 */
object VectorStore {
    fun topK(query: FloatArray, chunks: List<ChunkEntity>, k: Int): List<SourceChunk> = chunks
        .map { chunk ->
            SourceChunk(
                chunkIndex = chunk.chunkIndex,
                startPage = chunk.startPage,
                endPage = chunk.endPage,
                text = chunk.text,
                score = VectorMath.cosine(query, VectorMath.fromBytes(chunk.embedding)),
            )
        }
        .sortedByDescending { it.score }
        .take(k)
}
