package com.manjit.pocketmind.data.models

import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.core.model.LlmModelInfo

/** Download/installation status of one model, as the model screen shows it. */
sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus

    /** Waiting for the right network (e.g. Wi-Fi) or a retry after a network error. */
    data class Queued(val downloaded: Long, val total: Long) : ModelStatus

    data class Downloading(val downloaded: Long, val total: Long) : ModelStatus

    /** Stopped by the user; `model.part` is kept so the download can resume. */
    data class Paused(val downloaded: Long, val total: Long) : ModelStatus

    data class Failed(val kind: ErrorKind, val downloaded: Long, val total: Long) : ModelStatus

    /** Complete and SHA-256 verified. */
    data object Downloaded : ModelStatus
}

/** A model plus everything the screen needs to know about it on this phone. */
data class ModelState(val info: LlmModelInfo, val status: ModelStatus, val isSelected: Boolean, val fitsInRam: Boolean)

/** What WorkManager reports about a model's download job, reduced to what we need. */
data class DownloadWork(val phase: Phase, val downloaded: Long, val failure: ErrorKind?) {
    enum class Phase { ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
}

/**
 * Combines the three facts we have — the job state, the partial file, the final file — into
 * one [ModelStatus]. Pure function: tested without WorkManager or a file system.
 *
 * The final file wins over everything: it only exists after the checksum passed.
 */
object ModelStatusResolver {
    fun resolve(model: LlmModelInfo, work: DownloadWork?, finalExists: Boolean, partialBytes: Long): ModelStatus {
        val total = model.sizeBytes
        return when {
            finalExists -> ModelStatus.Downloaded

            work?.phase == DownloadWork.Phase.RUNNING ->
                ModelStatus.Downloading(maxOf(work.downloaded, partialBytes), total)

            work?.phase == DownloadWork.Phase.ENQUEUED -> ModelStatus.Queued(partialBytes, total)

            work?.phase == DownloadWork.Phase.FAILED ->
                ModelStatus.Failed(work.failure ?: ErrorKind.UNKNOWN, partialBytes, total)

            partialBytes > 0 -> ModelStatus.Paused(partialBytes, total)

            else -> ModelStatus.NotDownloaded
        }
    }
}
