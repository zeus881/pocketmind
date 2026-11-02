package com.sanjay.pocketmind.data.models

import com.sanjay.pocketmind.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Everything the model manager screen and onboarding need. */
interface ModelRepository {
    /** All catalog models with live download status, selection and RAM fit. */
    fun observeModels(): Flow<List<ModelState>>

    /** Marketed RAM of this phone in GB (e.g. 8). */
    fun deviceRamGb(): Int

    /** The chat model we recommend for this phone's RAM. */
    fun recommendedModelId(): String?

    /** Starts (or resumes) a download. Fails fast if storage is too small or the phone lacks RAM. */
    suspend fun download(modelId: String): AppResult<Unit>

    /** Stops the download but keeps the partial file for a later resume. */
    fun pause(modelId: String)

    /** Stops the download and deletes the partial file. */
    suspend fun cancel(modelId: String)

    /** Deletes a downloaded model and frees its storage. */
    suspend fun delete(modelId: String)

    /**
     * Re-checks a downloaded model's size and SHA-256. A damaged file is deleted (so the card
     * offers "Download" again) and CHECKSUM_MISMATCH is returned.
     */
    suspend fun verify(modelId: String): AppResult<Unit>

    /** Makes a downloaded chat model the one used for chatting. */
    suspend fun select(modelId: String)

    /** Free bytes needed before [modelId] can be downloaded (for the "need X GB" message). */
    fun requiredFreeBytes(modelId: String): Long
}
