package com.sanjay.pocketmind.data.models

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.data.models.work.ModelDownloadWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * [ModelRepository] built on WorkManager (jobs), [ModelFiles] (what's on disk) and settings.
 *
 * Status is DERIVED, never stored: WorkManager already persists job state and the files are
 * the truth about what is downloaded. Storing a separate "status" column would only create a
 * second truth that could disagree (e.g. after the app is killed mid-download).
 */
@Singleton
internal class WorkManagerModelRepository @Inject constructor(
    private val workManager: WorkManager,
    private val catalog: ModelCatalog,
    private val files: ModelFiles,
    private val deviceInfo: DeviceInfo,
    private val settings: SettingsDataSource,
    private val engine: LlmEngine,
    private val verifier: ModelVerifier,
    private val loadGuard: LoadGuard,
) : ModelRepository {

    /** File changes (delete/cancel) aren't observable, so we poke this to re-evaluate. */
    private val fileChanges = MutableStateFlow(0)

    override fun observeModels(): Flow<List<ModelState>> = combine(
        workManager.getWorkInfosByTagFlow(ModelDownloadWorker.TAG),
        settings.settings,
        fileChanges,
    ) { workInfos, userSettings, _ ->
        val ram = deviceInfo.totalRamGb()
        catalog.models.map { model ->
            val work = workInfos
                .filter { ModelDownloadWorker.modelTag(model.id) in it.tags }
                .maxByOrNull { it.runAttemptCount } // the newest attempt
                ?.toDownloadWork()
            ModelState(
                info = model,
                status = ModelStatusResolver.resolve(
                    model = model,
                    work = work,
                    finalExists = files.isDownloaded(model.id),
                    partialBytes = files.partialBytes(model.id),
                ),
                isSelected = userSettings.selectedModelId == model.id,
                fitsInRam = ModelRecommender.fitsInRam(model, ram),
            )
        }
    }

    override fun deviceRamGb(): Int = deviceInfo.totalRamGb()

    override fun recommendedModelId(): String? = ModelRecommender.recommend(catalog.models, deviceRamGb())?.id

    override fun requiredFreeBytes(modelId: String): Long =
        catalog.find(modelId)?.let { ModelRecommender.requiredFreeBytes(it, files.partialBytes(modelId)) } ?: 0L

    override suspend fun download(modelId: String): AppResult<Unit> {
        val model = catalog.find(modelId) ?: return AppResult.Failure(ErrorKind.UNKNOWN)
        if (deviceInfo.freeStorageBytes() < requiredFreeBytes(modelId)) {
            return AppResult.Failure(ErrorKind.NOT_ENOUGH_STORAGE)
        }
        val networkType = if (settings.current().allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to model.id))
            // Unmetered (Wi-Fi) by default so a 2.6 GB download never eats mobile data silently.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            // Start quickly while the user is watching; fall back to a normal job if quota is used up.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(ModelDownloadWorker.TAG)
            .addTag(ModelDownloadWorker.modelTag(model.id))
            .build()
        // REPLACE: "Resume" after pause/failure starts a fresh job (which resumes from model.part).
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.uniqueName(model.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return AppResult.Success(Unit)
    }

    override fun pause(modelId: String) {
        workManager.cancelUniqueWork(ModelDownloadWorker.uniqueName(modelId))
    }

    override suspend fun cancel(modelId: String) {
        pause(modelId)
        files.partFile(modelId).delete()
        fileChanges.value++
    }

    override suspend fun delete(modelId: String) {
        pause(modelId)
        // Never delete the file under a loaded model: unload first.
        val loaded = engine.state.value
        if (loaded is EngineState.Ready && loaded.modelPath == files.finalFile(modelId).path) engine.unload()
        // A crash record belongs to THIS file; a future download is a different file.
        loadGuard.clear(files.finalFile(modelId).path)
        files.delete(modelId)
        if (settings.current().selectedModelId == modelId) {
            // Fall back to another downloaded chat model, if there is one.
            val fallback = catalog.models.firstOrNull { it.kind == ModelKind.CHAT && files.isDownloaded(it.id) }
            settings.setSelectedModel(fallback?.id)
        }
        fileChanges.value++
    }

    override suspend fun verify(modelId: String): AppResult<Unit> {
        val result = verifier.verify(modelId)
        if (result is AppResult.Failure && result.kind == ErrorKind.CHECKSUM_MISMATCH) delete(modelId)
        return result
    }

    override suspend fun select(modelId: String) {
        val model = catalog.find(modelId) ?: return
        if (model.kind == ModelKind.CHAT && files.isDownloaded(modelId)) settings.setSelectedModel(modelId)
    }

    private fun WorkInfo.toDownloadWork(): DownloadWork {
        val phase = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadWork.Phase.ENQUEUED
            WorkInfo.State.RUNNING -> DownloadWork.Phase.RUNNING
            WorkInfo.State.SUCCEEDED -> DownloadWork.Phase.SUCCEEDED
            WorkInfo.State.FAILED -> DownloadWork.Phase.FAILED
            WorkInfo.State.CANCELLED -> DownloadWork.Phase.CANCELLED
        }
        val error = outputData.getString(ModelDownloadWorker.KEY_ERROR)
        return DownloadWork(
            phase = phase,
            downloaded = progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0L),
            failure = error?.let { name -> ErrorKind.entries.firstOrNull { it.name == name } },
        )
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
