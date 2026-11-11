package com.sanjay.pocketmind.core.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberTextSharer(): (text: String, title: String) -> Unit {
    // The view controller that hosts this Compose window (public API for library authors, Compose 1.12.2).
    val host = LocalUIViewController.current
    return remember(host) {
        { text, _ ->
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            // On iPad the sheet is a popover and crashes without an anchor; on iPhone this line does nothing.
            sheet.popoverPresentationController?.sourceView = host.view
            host.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

actual fun formatDateTime(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND))
}

private const val MILLIS_PER_SECOND = 1000.0
