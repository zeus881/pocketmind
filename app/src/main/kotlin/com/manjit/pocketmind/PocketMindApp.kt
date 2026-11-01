package com.manjit.pocketmind

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * The Application object: Android creates exactly one per app process, before any screen.
 *
 * `@HiltAndroidApp` makes Hilt generate the app-wide dependency container here. Every
 * `@Inject` in the app is ultimately served from this container.
 */
@HiltAndroidApp
class PocketMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Logs only in debug builds. Release builds log nothing, so no chat text can ever
        // leak into the phone's system log (privacy promise).
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
