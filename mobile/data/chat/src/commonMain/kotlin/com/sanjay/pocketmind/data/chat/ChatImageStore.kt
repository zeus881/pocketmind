package com.sanjay.pocketmind.data.chat

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
        EXIF_ROTATE_90 -> QUARTER_TURN
        EXIF_ROTATE_180 -> HALF_TURN
        EXIF_ROTATE_270 -> THREE_QUARTER_TURN
        else -> 0f
    }

    // EXIF "Orientation" tag values (EXIF 2.3 standard; the same numbers as AndroidX ExifInterface's
    // ORIENTATION_ROTATE_* constants, which shared code can't use).
    private const val EXIF_ROTATE_180 = 3
    private const val EXIF_ROTATE_90 = 6
    private const val EXIF_ROTATE_270 = 8

    private const val QUARTER_TURN = 90f
    private const val HALF_TURN = 180f
    private const val THREE_QUARTER_TURN = 270f
}
