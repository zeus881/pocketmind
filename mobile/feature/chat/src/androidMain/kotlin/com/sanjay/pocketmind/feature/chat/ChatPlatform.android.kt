package com.sanjay.pocketmind.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sanjay.pocketmind.data.chat.ImageScaling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberDocumentSaver(
    mimeType: String,
    onPicked: (uri: String) -> Unit,
): ((fileName: String) -> Unit)? {
    // The user picks where; the app only gets permission for that one file (Storage Access Framework).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher) { { name -> launcher.launch(name) } }
}

@Composable
internal actual fun rememberMicrophonePermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted()
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
internal actual fun rememberPhotoPicker(onPicked: (uri: String) -> Unit): (() -> Unit)? {
    // The system photo picker needs no storage permission: the app only sees the one photo picked.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

/**
 * Decoded off the main thread at about the size it is shown (a 220 dp preview never needs the full 1024 px file):
 * first only the bounds, then a power-of-two sample size.
 */
internal actual suspend fun loadThumbnail(path: String, targetPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    val options = BitmapFactory.Options().apply {
        inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
    }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
