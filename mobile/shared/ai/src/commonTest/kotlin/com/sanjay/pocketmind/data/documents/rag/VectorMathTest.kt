package com.sanjay.pocketmind.data.documents.rag

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The embedding BLOB format must never change: v2 phones already store document embeddings in it.
 * These tests run on every target (JVM and iOS); `VectorMathNioTest` (Android host) also compares with
 * `java.nio.ByteBuffer`, the code v2 used to write them.
 */
class VectorMathTest {
    @Test
    fun knownFloatsHaveTheirLittleEndianIeeeBytes() {
        // 1.0f = 0x3F800000, -2.0f = 0xC0000000, 0.5f = 0x3F000000: lowest byte first.
        val bytes = VectorMath.toBytes(floatArrayOf(1.0f, -2.0f, 0.5f))

        assertContentEquals(
            byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F, 0x00, 0x00, 0x00, 0xC0.toByte(), 0x00, 0x00, 0x00, 0x3F),
            bytes,
        )
    }

    @Test
    fun bytesRoundTripExactly() {
        val vector = floatArrayOf(0f, -0f, 1e-38f, 3.4028235e38f, -123.456f, Float.MIN_VALUE)

        val back = VectorMath.fromBytes(VectorMath.toBytes(vector))

        assertEquals(vector.map { it.toRawBits() }, back.map { it.toRawBits() })
    }

    @Test
    fun aBlobWithATruncatedFloatIsRejected() {
        assertFailsWith<IllegalArgumentException> { VectorMath.fromBytes(ByteArray(5)) }
    }

    @Test
    fun cosineOfParallelOppositeAndZeroVectors() {
        assertEquals(1f, VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), absoluteTolerance = 1e-6f)
        assertEquals(-1f, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(-3f, 0f)), absoluteTolerance = 1e-6f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)))
    }
}
