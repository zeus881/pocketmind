package com.sanjay.pocketmind

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import co.touchlab.kermit.Logger
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.common.diagnostics.CrashLog
import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.di.pocketMindModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import timber.log.Timber

/**
 * The Application object: Android creates exactly one per app process, before any screen.
 *
 * - It starts Koin, the app-wide dependency container (V3; v2 used Hilt). [koinModules] is `open` so
 *   end-to-end tests can swap the engine, database and Play modules for fakes.
 * - `workManagerFactory()` lets WorkManager create our workers through Koin, so `ModelDownloadWorker` gets its
 *   dependencies. (WorkManager's automatic start-up is disabled in the manifest for this to work.)
 */
open class PocketMindApp : Application() {

    private val llmEngine: LlmEngine by inject()

    private val appScope: CoroutineScope by inject(PmQualifiers.AppScope)

    private val modelKeepAlive: ModelKeepAlive by inject()

    private val crashLog: CrashLog by inject()

    private val pro: ProStatus by inject()

    /** Every module of the app (see di/AppModules.kt). */
    protected open fun koinModules(): List<Module> = pocketMindModules

    override fun onCreate() {
        super.onCreate()
        // Robolectric runs many Application instances in one JVM, and Koin's container is global: start fresh.
        // In the real app nothing was started yet, so this does nothing.
        stopKoin()
        startKoin {
            androidContext(this@PocketMindApp)
            workManagerFactory()
            modules(koinModules())
        }
        // First: so a crash during start-up is recorded too (kept on the phone; decision 1).
        crashLog.install()
        // Logs only in debug builds. Release builds log nothing, so no chat text can ever
        // leak into the phone's system log (privacy promise). Shared code logs with Kermit, which would
        // write to Logcat on its own: send it through Timber in EVERY build, so this one switch decides.
        Logger.setLogWriters(TimberLogWriter)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLogTree(java.io.File(filesDir, "debug.log")))
            // Report main-thread disk/network work and leaked resources in logcat (debug only).
            StrictModePolicies.enable()
        }
        // Unload the model some minutes after the user leaves the app (see ModelKeepAlive).
        ProcessLifecycleOwner.get().lifecycle.addObserver(modelKeepAlive)
        // Ask Play once per start who owns Pro (refunds, purchases on another phone, pending payments).
        appScope.launch { pro.refresh() }
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
