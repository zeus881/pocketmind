package com.sanjay.pocketmind.data.documents.rag

import kotlin.math.sqrt

/**
 * Vector helpers for retrieval.
 *
 * COSINE SIMILARITY measures the angle between two vectors: 1 = same direction (same meaning),
 * 0 = unrelated, −1 = opposite. It ignores vector LENGTH, so a long passage and a short
 * question can still match:  cos(a, b) = (a · b) / (|a| · |b|).
 */
object VectorMath {
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension (${a.size} vs ${b.size})" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    /**
     * Float vector → bytes for a Room BLOB: 4 bytes per float, IEEE 754 bits, little-endian (native on ARM).
     *
     * V3: plain bit operations instead of `java.nio.ByteBuffer` (iOS has no java.nio). The layout is exactly
     * v2's, so embeddings already stored on a phone stay readable (pinned by VectorMathTest).
     */
    fun toBytes(vector: FloatArray): ByteArray {
        val bytes = ByteArray(vector.size * Float.SIZE_BYTES)
        vector.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (byte in 0 until Float.SIZE_BYTES) {
                bytes[index * Float.SIZE_BYTES + byte] = (bits ushr (byte * Byte.SIZE_BITS)).toByte()
            }
        }
        return bytes
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "An embedding has 4 bytes per float, got ${bytes.size} bytes" }
        return FloatArray(bytes.size / Float.SIZE_BYTES) { index ->
            var bits = 0
            for (byte in 0 until Float.SIZE_BYTES) {
                val unsigned = bytes[index * Float.SIZE_BYTES + byte].toInt() and BYTE_MASK
                bits = bits or (unsigned shl (byte * Byte.SIZE_BITS))
            }
            Float.fromBits(bits)
        }
    }

    private const val BYTE_MASK = 0xFF
}
