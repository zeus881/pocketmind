package com.sanjay.pocketmind.data.models

import com.sanjay.pocketmind.core.common.dispatchers.Dispatcher
import com.sanjay.pocketmind.core.common.dispatchers.PmDispatchers
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Re-checks a downloaded model file against the catalog: exact size, then the full SHA-256.
 *
 * The download already verified the file once. This second check exists for files that changed
 * AFTER download: a storage fault, a restore from another phone, or a manual copy. Hashing 2.6 GB
 * takes several seconds, so it runs only on request ("Check file" in the model manager) and on
 * the IO dispatcher.
 *
 * A file that passes is known-good, so any crash recorded for it by [LoadGuard] was the
 * phone's GPU driver, not the file — but we still clear the record, because the user just asked
 * us to make this model usable again.
 */
class ModelVerifier @Inject constructor(
    private val catalog: ModelCatalog,
    private val files: ModelFiles,
    private val loadGuard: LoadGuard,
    @Dispatcher(PmDispatchers.IO) private val io: CoroutineDispatcher,
) {
    /** The model's file is being deleted or replaced: crash records for it no longer apply. */
    fun forgetCrashes(modelId: String) = loadGuard.clear(files.finalFile(modelId).path)

    /** Success = the file matches; CHECKSUM_MISMATCH = damaged; NO_MODEL = not downloaded. */
    suspend fun verify(modelId: String): AppResult<Unit> = withContext(io) {
        val model = catalog.find(modelId) ?: return@withContext AppResult.Failure(ErrorKind.UNKNOWN)
        val file = files.finalFile(modelId)
        if (!file.isFile) return@withContext AppResult.Failure(ErrorKind.NO_MODEL)
        // Size first: it's free and catches truncation without reading gigabytes.
        val intact = file.length() == model.sizeBytes && Sha256.matches(Sha256.ofFile(file), model.sha256)
        if (intact) {
            loadGuard.clear(file.path)
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(ErrorKind.CHECKSUM_MISMATCH)
        }
    }
}
