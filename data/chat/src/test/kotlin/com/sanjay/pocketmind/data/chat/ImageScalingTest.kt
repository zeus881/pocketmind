package com.sanjay.pocketmind.data.chat

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageScalingTest {
    @Test
    fun `a 12 MP photo is decoded at a quarter of its size, never below the target`() {
        assertEquals(2, ImageScaling.sampleSize(4000, 3000)) // 2000 px ≥ 1024, 1000 px would be < 1024
        assertEquals(1, ImageScaling.sampleSize(1500, 800))
        assertEquals(4, ImageScaling.sampleSize(3000, 8000)) // portrait: the longest side counts
    }

    @Test
    fun `fit keeps the aspect ratio and leaves small images alone`() {
        assertEquals(1024 to 768, ImageScaling.fit(2000, 1500))
        assertEquals(576 to 1024, ImageScaling.fit(1125, 2000))
        assertEquals(640 to 480, ImageScaling.fit(640, 480))
        assertEquals(1024 to 1, ImageScaling.fit(5000, 2)) // never a zero-pixel side
    }

    @Test
    fun `exif orientation becomes an upright rotation`() {
        assertEquals(90f, ImageScaling.rotationFor(ExifInterface.ORIENTATION_ROTATE_90), 0f)
        assertEquals(180f, ImageScaling.rotationFor(ExifInterface.ORIENTATION_ROTATE_180), 0f)
        assertEquals(270f, ImageScaling.rotationFor(ExifInterface.ORIENTATION_ROTATE_270), 0f)
        assertEquals(0f, ImageScaling.rotationFor(ExifInterface.ORIENTATION_NORMAL), 0f)
        assertEquals(0f, ImageScaling.rotationFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL), 0f)
    }
}
