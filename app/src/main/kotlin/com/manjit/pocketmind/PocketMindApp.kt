package com.manjit.pocketmind

import android.app.Application
import android.content.ComponentCallbacks2
import com.manjit.pocketmind.core.common.dispatchers.ApplicationScope
import com.manjit.pocketmind.core.llm.LlmEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The Application object: Android creates exactly one per app process, before any screen.
 *
 * `@HiltAndroidApp` makes Hilt generate the app-wide dependency container here. Every
 * `@Inject` in the app is ultimately served from this container.
 */
@HiltAndroidApp
class PocketMindApp : Application() {

    @Inject lateinit var llmEngine: LlmEngine

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Logs only in debug builds. Release builds log nothing, so no chat text can ever
        // leak into the phone's system log (privacy promise).
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    /**
     * Android calls this when memory is getting low. A loaded model holds 1–3 GB, so it is
     * by far the best thing to give back. If we don't, Android kills the whole app process.
     * The model is lazily re-loaded the next time a screen needs it.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (MemoryPressure.shouldUnloadModel(level)) {
            Timber.i("onTrimMemory(%d): unloading the model", level)
            appScope.launch { llmEngine.unload() }
        }
    }
}

/** Decides which onTrimMemory levels are "critical" enough to drop the model. */
internal object MemoryPressure {
    // Raw values of ComponentCallbacks2 constants. Most are deprecated since API 34 (Android
    // only sends UI_HIDDEN and BACKGROUND there), but older phones still send them all.
    private const val RUNNING_CRITICAL = 15
    private const val BACKGROUND = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

    /**
     * Unload when the app is running but the system is critically low on memory, or when
     * the app is in the background and on Android's kill list.
     */
    fun shouldUnloadModel(level: Int): Boolean = level == RUNNING_CRITICAL || level >= BACKGROUND
}
