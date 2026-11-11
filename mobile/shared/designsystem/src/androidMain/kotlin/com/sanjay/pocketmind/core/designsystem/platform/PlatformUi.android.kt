package com.sanjay.pocketmind.core.designsystem.platform

import android.Manifest
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date

@Composable
actual fun rememberTextSharer(): (text: String, title: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text, title ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
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
