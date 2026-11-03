package com.sanjay.pocketmind

import android.util.Log
import java.io.File
import java.time.Instant
import timber.log.Timber

/**
 * DEBUG builds only (planted in [PocketMindApp]): Timber lines also go to `files/debug.log`, readable with
 * `adb exec-out run-as com.sanjay.pocketmind cat files/debug.log`. Some phones (vivo/iQOO) hide app lines in
 * logcat. The file is capped at [MAX_BYTES] and lives in app-private storage; release builds never log.
 */
internal class FileLogTree(private val file: File) : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.INFO) return
        runCatching {
            if (file.length() > MAX_BYTES) file.writeText("")
            val error = t?.let { "\n" + Log.getStackTraceString(it) }.orEmpty()
            file.appendText("${Instant.now()} ${LEVELS[priority] ?: "?"} $tag: $message$error\n")
        }
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024L
        val LEVELS = mapOf(Log.INFO to "I", Log.WARN to "W", Log.ERROR to "E", Log.ASSERT to "A")
    }
}
