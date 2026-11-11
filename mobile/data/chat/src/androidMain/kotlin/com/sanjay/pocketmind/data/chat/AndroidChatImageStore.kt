package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import co.touchlab.kermit.Logger
import java.io.File
import java.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@OptIn(ExperimentalUuidApi::class) // Uuid.random(): random file names
internal class AndroidChatImageStore(private val context: Context, private val io: CoroutineDispatcher) :
    ChatImageStore {

    private val directory = File(context.filesDir, DIRECTORY)

    override suspend fun import(uri: String): String? = withContext(io) {
        try {
            copyScaled(uri.toUri())
        } catch (e: IOException) {
            Logger.w(e) { "Could not read the picked image" }
            null
        } catch (e: SecurityException) {
            Logger.w(e) { "No permission for the picked image" }
            null
        } catch (e: OutOfMemoryError) {
            Logger.w(e) { "Image too large to decode" }
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
        val file = File(directory, "${Uuid.random()}.jpg")
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
