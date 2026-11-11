package com.sanjay.pocketmind.core.common.diagnostics

/**
 * The crashes recorded on this phone, newest last (V3: the common view of Android's CrashLog, so the shared
 * Settings screen can build a diagnostic report without knowing how the platform stores them).
 */
fun interface CrashReports {
    fun entries(): List<String>
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
