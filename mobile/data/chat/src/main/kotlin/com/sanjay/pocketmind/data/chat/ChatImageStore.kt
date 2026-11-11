package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Photos attached to questions. A picked photo is copied (scaled down, upright, as JPEG) into the
 * app's PRIVATE storage, so the chat keeps showing it even after the user deletes the original,
 * and the model reads a small file instead of a 12-megapixel one.
 */
interface ChatImageStore {
    /** Copies the image behind the content [uri] into private storage; returns its path, or null if unreadable. */
    suspend fun import(uri: String): String?

    /** Deletes stored photos that are not in [keep] (photos of deleted chats). */
    suspend fun deleteAllExcept(keep: Set<String>)

    suspend fun delete(path: String)
}

/** The size arithmetic of [AndroidChatImageStore], kept pure so it is unit-tested. */
object ImageScaling {
    /**
     * Longest side of a stored photo. The vision encoder works on a few hundred pixels anyway;
     * 1024 keeps text in screenshots readable while a photo stays around 100–300 KB.
     */
    const val MAX_SIDE = 1024

    /**
     * The largest power-of-two `inSampleSize` that keeps both sides at least [maxSide], so the
     * decoder never builds a full 12-megapixel bitmap in memory just to shrink it.
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** Final size with the aspect ratio kept and the longest side at most [maxSide]. */
    fun fit(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** Degrees to rotate a photo so it is upright, from its EXIF orientation tag (mirroring is ignored). */
    fun rotationFor(exifOrientation: Int): Float = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
        ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
        ExifInterface.ORIENTATION_ROTATE_270 -> THREE_QUARTER_TURN
        else -> 0f
    }

    private const val QUARTER_TURN = 90f
    private const val HALF_TURN = 180f
    private const val THREE_QUARTER_TURN = 270f
}

internal class AndroidChatImageStore(private val context: Context, private val io: CoroutineDispatcher) :
    ChatImageStore {

    private val directory = File(context.filesDir, DIRECTORY)

    override suspend fun import(uri: String): String? = withContext(io) {
        try {
            copyScaled(uri.toUri())
        } catch (e: IOException) {
            Timber.w(e, "Could not read the picked image")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "No permission for the picked image")
            null
        } catch (e: OutOfMemoryError) {
            Timber.w(e, "Image too large to decode")
            null
        }
    }

    /** Decodes the picked image at a reduced size, or returns null if it is not a readable image. */
    private fun decodeReduced(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null // missing, or not an image
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun copyScaled(uri: Uri): String? {
        val resolver = context.contentResolver
        val decoded = decodeReduced(uri) ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val (width, height) = ImageScaling.fit(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
            postRotate(ImageScaling.rotationFor(orientation))
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        directory.mkdirs()
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return file.path
    }

    override suspend fun deleteAllExcept(keep: Set<String>) = withContext(io) {
        directory.listFiles().orEmpty().filter { it.path !in keep }.forEach { it.delete() }
    }

    override suspend fun delete(path: String) {
        withContext(io) { File(path).takeIf { it.parentFile == directory }?.delete() }
    }

    private companion object {
        const val DIRECTORY = "chat_images"
        const val JPEG_QUALITY = 85
    }
}
