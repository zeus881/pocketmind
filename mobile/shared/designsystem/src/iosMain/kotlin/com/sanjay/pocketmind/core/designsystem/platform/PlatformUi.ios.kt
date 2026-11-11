package com.sanjay.pocketmind.core.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localizedStringForLanguageCode
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject

@Composable
actual fun rememberTextSharer(): (text: String, title: String?) -> Unit {
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
    return formatter.stringFromDate(epochMillis.toNSDate())
}

actual fun formatRelativeTime(epochMillis: Long): String =
    NSRelativeDateTimeFormatter().localizedStringForDate(epochMillis.toNSDate(), relativeToDate = NSDate())

private fun Long.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1000.0

@Composable
actual fun rememberFileSizeFormatter(): (bytes: Long) -> String = remember {
    { bytes -> NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile) }
}

@Composable
actual fun rememberNotificationPermissionLauncher(onAnswered: () -> Unit): (() -> Unit)? = null

@OptIn(ExperimentalComposeUiApi::class) // ClipEntry.withPlainText is the iOS way to build a text entry (Compose 1.12.2)
actual fun plainTextClipEntry(label: String, text: String): ClipEntry = ClipEntry.withPlainText(text)

actual fun languageDisplayName(code: String): String =
    NSLocale.currentLocale.localizedStringForLanguageCode(code)?.replaceFirstChar { it.titlecase() } ?: code

@Composable
actual fun rememberPdfPicker(onPicked: (uri: String) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    // UIKit keeps only a weak reference to its delegate, so the composition holds the strong one.
    val delegate = remember { PdfPickerDelegate() }
    delegate.onPicked = onPicked
    return remember(host, delegate) {
        {
            // asCopy = true: iOS copies the file into the app's sandbox, so it can be read without
            // security-scoped access (the same "one file the user chose" rule as Android's picker).
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** Receives the picked file from UIKit and hands its URL to the screen. */
private class PdfPickerDelegate :
    NSObject(),
    UIDocumentPickerDelegateProtocol {
    var onPicked: (String) -> Unit = {}

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.absoluteString?.let(onPicked)
    }
}
