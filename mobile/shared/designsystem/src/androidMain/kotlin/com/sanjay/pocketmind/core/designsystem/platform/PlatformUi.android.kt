package com.sanjay.pocketmind.core.designsystem.platform

import android.content.Intent
import android.text.format.DateUtils
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
