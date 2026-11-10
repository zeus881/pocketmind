package com.sanjay.pocketmind.feature.chat.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.sanjay.pocketmind.data.chat.ImageScaling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A square, cropped preview of a stored photo. It is decoded off the main thread at about the
 * size it is shown (a 220 dp preview never needs the full 1024 px file). While loading, or if the
 * file is gone, a neutral image icon is shown instead.
 */
@Composable
internal fun PhotoThumbnail(path: String, size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(path, targetPx) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Image, contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun decodeThumbnail(path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
