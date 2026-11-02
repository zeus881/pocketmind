package com.sanjay.pocketmind.data.documents.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    /** Float vector → bytes for a Room BLOB (4 bytes per float, little-endian = native on ARM). */
    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also { floats.get(it) }
    }
}
