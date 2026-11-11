package com.sanjay.pocketmind.core.designsystem.platform

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun rememberTextSharer(): (text: String, title: String?, subject: String?) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text, title, subject ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .apply { subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) } }
            context.startActivity(Intent.createChooser(send, title))
        }
    }
}

actual fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

actual fun formatRelativeTime(epochMillis: Long): String = DateUtils.getRelativeTimeSpanString(epochMillis).toString()

@Composable
actual fun rememberFileSizeFormatter(): (bytes: Long) -> String {
    val context = LocalContext.current
    return remember(context) { { bytes -> Formatter.formatShortFileSize(context, bytes) } }
}

@Composable
actual fun rememberNotificationPermissionLauncher(onAnswered: () -> Unit): (() -> Unit)? {
    // Before Android 13 apps may post notifications without asking. SDK_INT never changes while the app runs,
    // so this early return always takes the same branch and the composition stays stable.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAnswered() }
    return remember(launcher) { { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}

actual fun plainTextClipEntry(label: String, text: String): ClipEntry = ClipEntry(ClipData.newPlainText(label, text))

actual fun languageDisplayName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
actual fun rememberPdfPicker(onPicked: (uri: String) -> Unit): () -> Unit {
    // OpenDocument = the Storage Access Framework picker; null = the user backed out.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher) { { launcher.launch(arrayOf(PDF_MIME)) } }
}

private const val PDF_MIME = "application/pdf"
