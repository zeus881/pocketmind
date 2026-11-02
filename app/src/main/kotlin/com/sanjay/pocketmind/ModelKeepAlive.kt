package com.sanjay.pocketmind

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sanjay.pocketmind.core.common.dispatchers.ApplicationScope
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Unloads the model a while AFTER the user leaves the app, not the moment they leave.
 *
 * WHY: v1 unloaded on `TRIM_MEMORY_BACKGROUND`. Since Android 14 that level is sent for ordinary
 * backgrounding, not only under memory pressure, so every quick app switch cost a 5–13 s model
 * reload (AUDIT H-4). Now:
 * - the whole app goes to the background ([onStop] of the PROCESS lifecycle) → start a timer
 *   (the user's "keep the model loaded" setting, 5 min by default);
 * - the app comes back before it fires ([onStart]) → cancel it, the model is still warm;
 * - real memory pressure is still handled immediately by [MemoryPressure] in `onTrimMemory`.
 *
 * Observes `ProcessLifecycleOwner`, which reports the app as a whole (all activities), so a
 * rotation or moving between our own screens never counts as "leaving".
 */
@Singleton
class ModelKeepAlive @Inject constructor(
    private val engine: LlmEngine,
    private val settings: SettingsDataSource,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private var pendingUnload: Job? = null

    override fun onStop(owner: LifecycleOwner) {
        pendingUnload?.cancel()
        pendingUnload = scope.launch {
            val minutes = settings.current().keepModelLoadedMinutes
            delay(minutes * MILLIS_PER_MINUTE)
            Timber.i("App in background for %d min: unloading the model", minutes)
            engine.unload()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        pendingUnload?.cancel()
        pendingUnload = null
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
