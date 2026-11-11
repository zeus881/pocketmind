package com.sanjay.pocketmind.data.models.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.data.models.DeviceInfo
import com.sanjay.pocketmind.data.models.ModelCatalog
import com.sanjay.pocketmind.data.models.ModelRecommender
import com.sanjay.pocketmind.data.models.R
import com.sanjay.pocketmind.data.models.download.ModelDownloader
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Downloads one model in the background with WorkManager.
 *
 * WHY WorkManager? A 2.6 GB download takes minutes. WorkManager:
 * - keeps it running when the user leaves the app (as a foreground service with a notification),
 * - waits for the right network (Wi-Fi by default) using Constraints,
 * - retries with backoff after network errors, and
 * - survives the app being killed or the phone rebooting (jobs are persisted).
 *
 * WorkManager creates workers itself and passes Context and WorkerParameters. V3: Koin's worker factory
 * (`workerOf(::ModelDownloadWorker)` in modelsDataModule, `workManagerFactory()` at app start) fills in our own
 * dependencies; in v2 Hilt did that with assisted injection.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val catalog: ModelCatalog,
    private val downloader: ModelDownloader,
    private val deviceInfo: DeviceInfo,
    private val files: ModelFiles,
    private val settings: SettingsDataSource,
    private val loadGuard: LoadGuard,
) : CoroutineWorker(context, params) {

    private val notificationId = inputData.getString(KEY_MODEL_ID).hashCode()

    override suspend fun doWork(): Result {
        val model = inputData.getString(KEY_MODEL_ID)?.let(catalog::find) ?: return fail(ErrorKind.UNKNOWN)

        // Storage check BEFORE any byte is written: never fill the phone.
        val needed = ModelRecommender.requiredFreeBytes(model, files.partialBytes(model.id))
        if (deviceInfo.freeStorageBytes() < needed) return fail(ErrorKind.NOT_ENOUGH_STORAGE)

        // Promote to a foreground service: Android won't kill it while the notification shows.
        // Android 12+ forbids starting a foreground service while the app is in the background
        // (e.g. a retry after a network drop): then we continue as normal background work —
        // the .part file keeps progress even if the system stops us later.
        try {
            setForeground(foregroundInfo(model, files.partialBytes(model.id)))
        } catch (e: IllegalStateException) {
            Timber.w(e, "Foreground not allowed now; continuing in the background")
        }

        val result = try {
            downloader.download(model) { downloaded, total ->
                setProgress(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
                notify(model, downloaded)
            }
        } catch (e: CancellationException) {
            // Android 15+ gives a "dataSync" foreground service at most 6 hours per day. A very slow
            // 2.6 GB download can hit that limit; Android then stops us. model.part keeps every byte,
            // and WorkManager runs the job again later, so we only explain what happened.
            if (stoppedByForegroundTimeout()) notifyPausedBySystem(model)
            throw e
        }
        return when (result) {
            is AppResult.Success -> {
                // A fresh, verified file: crashes recorded for the old file no longer apply.
                loadGuard.clear(result.value.path)
                // First chat model ever downloaded: select it so the user can chat right away.
                if (model.kind == ModelKind.CHAT && settings.current().selectedModelId == null) {
                    settings.setSelectedModel(model.id)
                }
                Result.success()
            }

            is AppResult.Failure -> when {
                // Network errors are usually temporary: retry with backoff, resuming from .part.
                result.kind == ErrorKind.NETWORK && runAttemptCount < MAX_ATTEMPTS -> Result.retry()

                else -> fail(result.kind)
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val model = inputData.getString(KEY_MODEL_ID)?.let(catalog::find)
        return foregroundInfo(model, model?.let { files.partialBytes(it.id) } ?: 0)
    }

    private fun fail(kind: ErrorKind) = Result.failure(workDataOf(KEY_ERROR to kind.name))

    private fun stoppedByForegroundTimeout(): Boolean = isStopped &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT

    /** A normal (dismissible) notification replacing the ongoing progress one. */
    private fun notifyPausedBySystem(model: LlmModelInfo) {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(applicationContext.getString(R.string.data_models_notification_title, model.displayName))
            .setContentText(applicationContext.getString(R.string.data_models_notification_paused_by_system))
            .setOnlyAlertOnce(true)
            .build()
        post(notification)
    }

    /**
     * Posts a notification if Android 13+'s POST_NOTIFICATIONS permission allows it. The user can
     * revoke the permission at any moment (even between the check and the call), hence the catch.
     * The download itself never depends on notifications.
     */
    private fun post(notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission revoked")
        }
    }

    private fun foregroundInfo(model: LlmModelInfo?, downloaded: Long): ForegroundInfo {
        val notification = buildNotification(model, downloaded)
        // Android 10+ wants the service type; it must match the manifest (dataSync).
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun notify(model: LlmModelInfo, downloaded: Long) {
        post(buildNotification(model, downloaded))
    }
    private fun buildNotification(model: LlmModelInfo?, downloaded: Long): android.app.Notification {
        ensureChannel()
        val total = model?.sizeBytes ?: 0L
        val percent = if (total > 0) (downloaded * PERCENT / total).toInt() else 0
        // "Pause" cancels the job; model.part stays on disk, so "Resume" continues from there.
        val pause = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val title = applicationContext.getString(R.string.data_models_notification_title, model?.displayName.orEmpty())
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(applicationContext.getString(R.string.data_models_notification_progress, percent))
            .setProgress(PERCENT, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, applicationContext.getString(R.string.data_models_notification_pause), pause)
            .build()
    }

    /** Channels exist since Android 8 (API 26) = our minSdk, so no version check is needed. */
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.data_models_notification_channel),
            NotificationManager.IMPORTANCE_LOW, // no sound for a progress bar
        )
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val TAG = "model-download"
        private const val CHANNEL_ID = "model_downloads"
        private const val MAX_ATTEMPTS = 5
        private const val PERCENT = 100

        /** One unique job per model, so tapping "Download" twice never starts two downloads. */
        fun uniqueName(modelId: String) = "download-$modelId"

        fun modelTag(modelId: String) = "model:$modelId"
    }
}
