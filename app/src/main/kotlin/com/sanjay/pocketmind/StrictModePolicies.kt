package com.sanjay.pocketmind

import android.os.Build
import android.os.StrictMode

/**
 * StrictMode for DEBUG builds: Android reports (in logcat, tag "StrictMode") every time the app
 * does something that can cause jank or an ANR, or leaks a resource.
 *
 * - Thread policy (main thread): disk reads/writes, network, slow calls, unbuffered I/O.
 *   On the main thread these freeze the UI; 5 s frozen = "App isn't responding" (ANR).
 * - VM policy (whole process): leaked Closeables/cursors/SQLite objects, leaked activities,
 *   leaked BroadcastReceiver registrations, file:// URIs given to other apps, cleartext HTTP.
 *
 * Penalty = log only. A crash penalty would also fire for violations inside libraries we don't
 * control; logging lets us read and fix ours. Called only when BuildConfig.DEBUG, so R8 removes
 * all of this from release builds.
 */
internal object StrictModePolicies {
    fun enable() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectUnbufferedIo()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .detectCleartextNetwork()
                .detectContentUriWithoutPermission()
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) detectUnsafeIntentLaunch() }
                .penaltyLog()
                .build(),
        )
    }
}
