package com.sanjay.pocketmind.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The system "Save as" screen for a chat export of [mimeType]; [onPicked] gets the chosen location as text. Null where
 * chat export isn't available yet (iOS: its ChatExporter comes in V3 phase 2d), so the export menu is hidden.
 */
@Composable
internal expect fun rememberDocumentSaver(
    mimeType: String,
    onPicked: (uri: String) -> Unit,
): ((fileName: String) -> Unit)?

/**
 * Returns "start voice input": it makes sure the app may use the microphone, then calls [onGranted]. Android asks
 * with its permission dialog the first time; iOS asks by itself when recording first starts.
 */
@Composable
internal expect fun rememberMicrophonePermission(onGranted: () -> Unit): () -> Unit

/**
 * Opens the system photo picker for ONE image and gives its location as text. Null where photos can't be attached
 * yet (iOS: its ChatImageStore comes in V3 phase 2d), so the photo button is hidden.
 */
@Composable
internal expect fun rememberPhotoPicker(onPicked: (uri: String) -> Unit): (() -> Unit)?

/** The stored photo at [path], decoded at about [targetPx] on its longest side; null if unreadable or gone. */
internal expect suspend fun loadThumbnail(path: String, targetPx: Int): ImageBitmap?
