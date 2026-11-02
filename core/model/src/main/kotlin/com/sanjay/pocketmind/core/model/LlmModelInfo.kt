package com.sanjay.pocketmind.core.model

import kotlinx.serialization.Serializable

/**
 * One downloadable model, as listed in `assets/models.json`.
 *
 * Every value in the catalog is real and verifiable:
 * - [url] is a Hugging Face "resolve" URL pinned to a COMMIT HASH, so the file behind it can
 *   never change silently.
 * - [sha256] is the file's checksum (Hugging Face's LFS id is the SHA-256), checked after download.
 */
@Serializable
data class LlmModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val kind: ModelKind = ModelKind.CHAT,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val minRamGb: Int,
    val license: String,
)

/** What a model is used for. */
@Serializable
enum class ModelKind {
    /** A chat/instruction model run by the LLM engine. */
    CHAT,

    /** A text-embedding model used by "Ask your PDF" (phase 7). */
    EMBEDDING,
}
