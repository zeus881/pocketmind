package com.sanjay.pocketmind.data.models

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 helpers.
 *
 * A SHA-256 checksum is a 64-character "fingerprint" of a file. Change one bit anywhere in a
 * 2.6 GB file and the fingerprint is completely different. We compare the downloaded file's
 * fingerprint with the one in the catalog to detect corruption or tampering.
 *
 * The digest is fed incrementally ([MessageDigest.update]) so we never hold the file in memory.
 */
object Sha256 {
    private const val BUFFER_SIZE = 1 shl 20 // 1 MiB

    fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun ofBytes(data: ByteArray): String = hex(newDigest().digest(data))

    /** Feeds the first [length] bytes of [file] into [digest] (used when resuming a download). */
    fun update(digest: MessageDigest, file: File, length: Long = file.length()) {
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
    }

    fun ofFile(file: File): String = newDigest().also { update(it, file) }.digest().let(::hex)

    /** Constant-format comparison (case-insensitive hex). */
    fun matches(actualHex: String, expectedHex: String): Boolean = actualHex.equals(expectedHex, ignoreCase = true)
}
