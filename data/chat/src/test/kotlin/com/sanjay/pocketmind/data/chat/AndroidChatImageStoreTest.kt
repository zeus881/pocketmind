package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The real photo import with real JPEG bytes: Robolectric's NATIVE graphics mode decodes and
 * encodes images like a phone does, so the scaling and saving are tested end to end.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidChatImageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = AndroidChatImageStore(context, Dispatchers.Unconfined)

    private fun photo(width: Int, height: Int): String {
        val file = File(context.cacheDir, "picked-${width}x$height.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return Uri.fromFile(file).toString()
    }

    private fun size(path: String): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun `a large photo is stored privately, scaled to 1024 px with its shape kept`() = runTest {
        val path = checkNotNull(store.import(photo(3000, 1500)))

        assertTrue(path.startsWith(File(context.filesDir, "chat_images").path))
        assertEquals(1024 to 512, size(path))
    }

    @Test
    fun `a small photo keeps its size`() = runTest {
        val path = checkNotNull(store.import(photo(640, 480)))

        assertEquals(640 to 480, size(path))
    }

    @Test
    fun `a file that is not an image is refused`() = runTest {
        val text = File(context.cacheDir, "notes.txt").apply { writeText("not a picture") }

        assertNull(store.import(Uri.fromFile(text).toString()))
        assertNull(store.import(Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString()))
    }

    @Test
    fun `cleanup keeps only the photos still in use, and delete stays inside the photo folder`() = runTest {
        val keep = checkNotNull(store.import(photo(100, 100)))
        val old = checkNotNull(store.import(photo(200, 200)))
        val outside = File(context.cacheDir, "other.jpg").apply { writeText("x") }

        store.deleteAllExcept(setOf(keep))
        store.delete(outside.path)

        assertTrue(File(keep).exists())
        assertFalse(File(old).exists())
        assertTrue(outside.exists()) // never deletes files that aren't stored photos

        store.delete(keep)
        assertFalse(File(keep).exists())
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
