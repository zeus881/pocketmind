package com.sanjay.pocketmind.data.documents.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * V3 replaced `java.nio.ByteBuffer` in [VectorMath] with plain bit operations (iOS has no java.nio). This
 * JVM-only test runs v2's exact code next to the new one: the bytes must be identical, so embeddings that v2
 * already wrote to a phone's database are read back unchanged.
 */
class VectorMathNioTest {
    @Test
    fun newBytesEqualTheOldByteBufferBytes() {
        val random = Random(seed = 768)
        repeat(20) {
            // 768 = EmbeddingGemma's vector size.
            val vector = FloatArray(768) { random.nextFloat() * 2 - 1 }

            assertContentEquals(v2ToBytes(vector), VectorMath.toBytes(vector))
            assertContentEquals(vector, VectorMath.fromBytes(v2ToBytes(vector)))
        }
    }

    /** v2's VectorMath.toBytes, unchanged. */
    private fun v2ToBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }
}
