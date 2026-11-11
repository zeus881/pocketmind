package com.sanjay.pocketmind.data.models

import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind

/**
 * Pure rules that match models to a phone. No Android code → trivially unit-tested.
 *
 * - 8 GB+ RAM → Gemma 4 E2B, 6 GB → Qwen3.5 0.8B, 4 GB → Qwen3 0.6B (from the catalog's minRamGb).
 * - Free storage must be at least size × 1.2: the extra 20% leaves room for the OS and for
 *   the app's own database and caches, so the download can't fill the phone completely.
 */
object ModelRecommender {
    const val STORAGE_SAFETY_FACTOR = 1.2

    /** The largest chat model that fits in [ramGb]; if none fits, the smallest one. */
    fun recommend(models: List<LlmModelInfo>, ramGb: Int): LlmModelInfo? {
        val chat = models.filter { it.kind == ModelKind.CHAT }
        return chat.filter { it.minRamGb <= ramGb }.maxByOrNull { it.sizeBytes } ?: chat.minByOrNull { it.sizeBytes }
    }

    fun fitsInRam(model: LlmModelInfo, ramGb: Int): Boolean = ramGb >= model.minRamGb

    fun requiredFreeBytes(model: LlmModelInfo, alreadyDownloaded: Long = 0): Long =
        ((model.sizeBytes - alreadyDownloaded).coerceAtLeast(0) * STORAGE_SAFETY_FACTOR).toLong()
}
