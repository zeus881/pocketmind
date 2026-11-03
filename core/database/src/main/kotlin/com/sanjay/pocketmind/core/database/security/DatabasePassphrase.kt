package com.sanjay.pocketmind.core.database.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The secret that opens the encrypted database. */
fun interface DatabasePassphrase {
    fun get(): ByteArray
}

/** Encrypts and decrypts a small secret with a key that never leaves secure hardware. */
interface KeyWrapper {
    fun wrap(secret: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * A random 256-bit database key, created on first use. Only its WRAPPED form is stored, in
 * [keyFile] (the app's no-backup folder); unwrapping needs the Android Keystore key, which is bound
 * to this phone and this app install. A copied database file or key file is useless elsewhere.
 *
 * The key is used as a hex string: SQLCipher derives the real cipher key from it (PBKDF2), the same
 * way for Room's open helper and for the one-time `ATTACH … KEY` migration.
 */
class WrappedDatabasePassphrase(private val keyFile: File, private val wrapper: KeyWrapper) : DatabasePassphrase {
    private var cached: ByteArray? = null

    @Synchronized
    override fun get(): ByteArray = cached?.copyOf() ?: load().also { cached = it }.copyOf()

    private fun load(): ByteArray {
        if (keyFile.exists()) return wrapper.unwrap(keyFile.readBytes())
        val raw = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
        raw.fill(0)
        keyFile.parentFile?.mkdirs()
        // Write-then-rename: a crash mid-write never leaves a half key that would lock the data away.
        val temporary = File(keyFile.path + ".tmp")
        temporary.writeBytes(wrapper.wrap(passphrase))
        check(temporary.renameTo(keyFile)) { "could not store the database key" }
        return passphrase
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

/** [KeyWrapper] with AES-256-GCM in the Android Keystore. Output: 12-byte IV followed by the ciphertext. */
class KeystoreKeyWrapper(private val alias: String = DEFAULT_ALIAS) : KeyWrapper {
    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(secret)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
        }
        return cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_ALIAS = "pocketmind.database"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val AES_BITS = 256
    }
}
