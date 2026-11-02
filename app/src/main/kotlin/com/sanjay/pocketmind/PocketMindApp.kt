package com.sanjay.pocketmind

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.sanjay.pocketmind.core.common.dispatchers.ApplicationScope
import com.sanjay.pocketmind.core.llm.LlmEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The Application object: Android creates exactly one per app process, before any screen.
 *
 * - `@HiltAndroidApp` generates the app-wide dependency container.
 * - `Configuration.Provider` lets WorkManager create our workers through Hilt
 *   ([HiltWorkerFactory]), so `ModelDownloadWorker` can get its dependencies injected.
 *   (WorkManager's automatic start-up is disabled in the manifest for this to work.)
 */
@HiltAndroidApp
class PocketMindApp :
    Application(),
    Configuration.Provider {

    @Inject lateinit var llmEngine: LlmEngine

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var modelKeepAlive: ModelKeepAlive

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Logs only in debug builds. Release builds log nothing, so no chat text can ever
        // leak into the phone's system log (privacy promise).
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Unload the model some minutes after the user leaves the app (see ModelKeepAlive).
        ProcessLifecycleOwner.get().lifecycle.addObserver(modelKeepAlive)
    }

    /**
     * Android calls this when memory is getting low. A loaded model holds 1–3 GB, so it is
     * by far the best thing to give back. If we don't, Android kills the whole app process.
     * The model is lazily re-loaded the next time a screen needs it. Ordinary backgrounding
     * is handled by [ModelKeepAlive] instead.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (MemoryPressure.shouldUnloadModel(level)) {
            Timber.i("onTrimMemory(%d): unloading the model", level)
            appScope.launch { llmEngine.unload() }
        }
    }
}

/** Decides which onTrimMemory levels mean REAL memory pressure, worth dropping the model at once. */
internal object MemoryPressure {
    // Raw values of ComponentCallbacks2 constants. Most are deprecated since API 34 (Android
    // only sends UI_HIDDEN and BACKGROUND there), but older phones still send them all.
    private const val RUNNING_CRITICAL = 15
    private const val MODERATE = 60 // TRIM_MEMORY_MODERATE: in the background, middle of the kill list

    /**
     * Unload when the app is running but the system is critically low on memory, or when the
     * app is in the background and already in the middle or end of Android's kill list.
     * UI_HIDDEN (20) and BACKGROUND (40) are NOT pressure on Android 14+: they arrive on every
     * app switch, and [ModelKeepAlive] handles that with a timer.
     */
    fun shouldUnloadModel(level: Int): Boolean = level == RUNNING_CRITICAL || level >= MODERATE
}
