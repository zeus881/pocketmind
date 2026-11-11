package com.sanjay.pocketmind

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import timber.log.Timber

/**
 * Sends the shared (KMP) code's Kermit logs to Timber (V3).
 *
 * Why: Kermit, by default, writes straight to Logcat in every build. The app's privacy promise is that release
 * builds log nothing, and in v2 that was guaranteed by planting Timber trees only in debug builds. Routing Kermit
 * through Timber keeps that single switch: release = no trees = no log lines; debug = Logcat + debug.log file.
 */
internal object TimberLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val tree = if (tag.isEmpty()) Timber else Timber.tag(tag)
        when (severity) {
            Severity.Verbose -> tree.v(throwable, message)
            Severity.Debug -> tree.d(throwable, message)
            Severity.Info -> tree.i(throwable, message)
            Severity.Warn -> tree.w(throwable, message)
            Severity.Error -> tree.e(throwable, message)
            Severity.Assert -> tree.wtf(throwable, message)
        }
    }
}
