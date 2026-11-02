package com.sanjay.pocketmind.core.testing

import com.sanjay.pocketmind.core.llm.TextEmbedder
import kotlin.math.sqrt

/**
 * A deterministic stand-in for the embedding model, for unit tests.
 *
 * It is a "bag of words" hash embedding: every word adds 1 to one of [DIMENSIONS] buckets, then
 * the vector is normalized. Texts sharing words therefore have high cosine similarity, which is
 * enough to test retrieval logic (ranking, top-k, citations) without a 165 MB model.
 */
class FakeTextEmbedder : TextEmbedder {
    var loadedPath: String? = null
        private set
    val embeddedDocuments = mutableListOf<String>()

    override suspend fun load(modelPath: String) {
        loadedPath = modelPath
    }

    override suspend fun embedDocuments(texts: List<String>): List<FloatArray> {
        embeddedDocuments += texts
        return texts.map(::vector)
    }

    override suspend fun embedQuery(text: String): FloatArray = vector(text)

    override suspend fun unload() {
        loadedPath = null
    }

    private fun vector(text: String): FloatArray {
        val v = FloatArray(DIMENSIONS)
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.forEach { word ->
            v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f
        }
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm == 0f) v else FloatArray(DIMENSIONS) { v[it] / norm }
    }

    companion object {
        const val DIMENSIONS = 64
    }
}
