package com.sanjay.pocketmind.core.common.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.sanjay.pocketmind.core.common.time.Clock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * The last crashes, kept ON THE PHONE ONLY (decision 1: no crash SDK, nothing sent automatically).
 * The user can share them in a diagnostic report if they choose to. Stack traces contain code
 * locations, never chat text.
 */
class CrashLog internal constructor(private val file: File, private val clock: Clock) {
    constructor(context: Context, clock: Clock) : this(File(context.filesDir, FILE), clock)

    /** Records uncaught exceptions, then lets Android handle them as before (the crash dialog). */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error) } // a failing log must never hide the real crash
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun record(threadName: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().take(MAX_TRACE)
        val entry = "== ${Instant.ofEpochMilli(clock.nowMillis())} on thread $threadName\n${trace.trimEnd()}"
        val kept = entries().takeLast(KEPT_CRASHES - 1) + entry
        file.writeText(kept.joinToString("") { it + SEPARATOR })
    }

    /** The recorded crashes, oldest first. */
    @Synchronized
    fun entries(): List<String> {
        if (!file.exists()) return emptyList()
        return file.readText().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val FILE = "crash.log"

        /** A line no stack trace contains (traces can have blank lines, so those can't separate entries). */
        const val SEPARATOR = "\n--- end of crash ---\n"
        const val KEPT_CRASHES = 3
        const val MAX_TRACE = 8_000
    }
}

/** Facts about the app and the phone for a diagnostic report. Nothing personal. */
data class DeviceFacts(
    val appVersion: String,
    val androidVersion: String,
    val sdk: Int,
    val device: String,
    val totalRamGb: Int,
)

fun interface DeviceFactsSource {
    fun facts(): DeviceFacts
}

internal class AndroidDeviceFacts(private val context: Context) : DeviceFactsSource {
    override fun facts(): DeviceFacts {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        return DeviceFacts(
            appVersion = version,
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            totalRamGb = ((memory.totalMem + GIB / 2) / GIB).toInt(),
        )
    }

    private companion object {
        const val GIB = 1024L * 1024 * 1024
    }
}

/**
 * The text the user shares ("Share diagnostic report"). It is shown in the share sheet, so the user
 * sees every word before anything leaves the phone; it never contains chats, notes or prompts.
 */
object DiagnosticReport {
    fun build(device: DeviceFacts, app: Map<String, String>, crashes: List<String>): String = buildString {
        appendLine("PocketMind diagnostic report")
        appendLine("App version: ${device.appVersion}")
        appendLine("Android: ${device.androidVersion} (API ${device.sdk})")
        appendLine("Device: ${device.device}, ${device.totalRamGb} GB RAM")
        app.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine()
        if (crashes.isEmpty()) {
            appendLine("No crashes recorded.")
        } else {
            appendLine("Last crashes (${crashes.size}):")
            crashes.forEach { appendLine(it).appendLine() }
        }
        append("This report contains no chats, notes, prompts or documents.")
    }
}
