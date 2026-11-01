package com.manjit.pocketmind.core.llm

/**
 * Turns text into a vector of numbers ("embedding") such that texts with similar MEANING get
 * vectors pointing in similar directions. That's the heart of "Ask your PDF": we find the
 * document chunks whose vectors are closest to the question's vector.
 *
 * Separate from [LlmEngine] because it is a different, much smaller model (~165 MB) with a
 * different job: it never generates text, it only measures meaning.
 */
interface TextEmbedder {
    /** Loads the embedding model (no-op if already loaded). */
    suspend fun load(modelPath: String)

    /** Embeds document passages (for indexing). Vectors are L2-normalized. */
    suspend fun embedDocuments(texts: List<String>): List<FloatArray>

    /** Embeds a user question (for searching). Vectors are L2-normalized. */
    suspend fun embedQuery(text: String): FloatArray

    suspend fun unload()
}

/** Where the downloaded embedding model is, or null if it isn't downloaded yet. */
interface EmbeddingModelSource {
    suspend fun embeddingModelPath(): String?

    /** Catalog id of the embedding model (stored with each index, to detect model changes). */
    fun embeddingModelId(): String?
}

/**
 * EmbeddingGemma was trained with short task prefixes and works best when queries and documents
 * are marked differently (an "asymmetric" search: short question vs. long passage).
 */
object EmbeddingPrompts {
    fun document(text: String) = "title: none | text: $text"

    fun query(text: String) = "task: search result | query: $text"
}
