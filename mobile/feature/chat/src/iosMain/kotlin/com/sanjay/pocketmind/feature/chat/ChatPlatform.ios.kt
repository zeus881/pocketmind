package com.sanjay.pocketmind.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** No chat export on iOS yet (its ChatExporter comes in V3 phase 2d): the export menu is hidden. */
@Composable
internal actual fun rememberDocumentSaver(
    mimeType: String,
    onPicked: (uri: String) -> Unit,
): ((fileName: String) -> Unit)? = null

/**
 * iOS shows its microphone and speech-recognition questions itself the first time recording starts (the iOS
 * SpeechInput, phase 2d), so there is nothing to ask beforehand.
 */
@Composable
internal actual fun rememberMicrophonePermission(onGranted: () -> Unit): () -> Unit = onGranted

/** No photo attachments on iOS yet (its ChatImageStore comes in V3 phase 2d): the photo button is hidden. */
@Composable
internal actual fun rememberPhotoPicker(onPicked: (uri: String) -> Unit): (() -> Unit)? = null

/** No stored photos exist on iOS yet (see [rememberPhotoPicker]); the thumbnail shows its placeholder icon. */
internal actual suspend fun loadThumbnail(path: String, targetPx: Int): ImageBitmap? = null
