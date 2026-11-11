package com.sanjay.pocketmind.feature.chat.component

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.sanjay.pocketmind.feature.chat.loadThumbnail

/**
 * A square, cropped preview of a stored photo. It is decoded off the main thread at about the
 * size it is shown (a 220 dp preview never needs the full 1024 px file). While loading, or if the
 * file is gone, a neutral image icon is shown instead.
 */
@Composable
internal fun PhotoThumbnail(path: String, size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = loadThumbnail(path, targetPx)
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
