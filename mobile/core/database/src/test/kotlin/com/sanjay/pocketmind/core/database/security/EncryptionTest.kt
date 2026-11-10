package com.sanjay.pocketmind.core.database.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The encryption logic that doesn't need SQLCipher's native code: the safe migration steps, header
 * detection, the key file, and which open helper is used. The real cipher runs in EncryptionDeviceTest.
 */
@RunWith(RobolectricTestRunner::class)
class EncryptionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var dir: File
    private lateinit var database: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "encryption-test").apply {
            deleteRecursively()
            mkdirs()
        }
        database = File(dir, "pocketmind.db")
    }

    private fun createPlainDatabase(file: File = database) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE chats (id TEXT PRIMARY KEY)")
            db.execSQL("INSERT INTO chats VALUES ('c1')")
        }
    }

    /** Pretends to encrypt: writes random-looking bytes (no SQLite header) and reports chosen counts. */
    private class FakeCipher(
        var originalCounts: Map<String, Long> = mapOf("chats" to 1),
        var copyCounts: Map<String, Long> = mapOf("chats" to 1),
        var failExport: Boolean = false,
    ) : CipherOperations {
        override fun exportEncrypted(plain: File, target: File, passphrase: ByteArray) {
            check(!failExport) { "export failed" }
            target.writeBytes(ByteArray(4096) { (it * 31 + 7).toByte() })
        }

        override fun rowCounts(database: File, passphrase: ByteArray?) =
            if (passphrase == null) originalCounts else copyCounts
    }

    private val key = "00ff".toByteArray()

    @Test
    fun `a plain sqlite file is recognised by its header, an encrypted one is not`() {
        createPlainDatabase()
        assertTrue(SqliteHeader.isPlaintext(database))
        val noise = File(dir, "noise.db").apply { writeBytes(ByteArray(100) { 1 }) }
        assertFalse(SqliteHeader.isPlaintext(noise))
        assertFalse(SqliteHeader.isPlaintext(File(dir, "missing.db")))
    }

    @Test
    fun `a verified copy replaces the plain database and old journals are removed`() {
        createPlainDatabase()
        File(database.path + "-wal").writeText("old")

        val outcome = EncryptionMigrator(FakeCipher()).migrate(database, key)

        assertEquals(MigrationOutcome.MIGRATED, outcome)
        assertFalse(SqliteHeader.isPlaintext(database))
        assertFalse(File(database.path + "-wal").exists())
        assertFalse(File(database.path + EncryptionMigrator.BACKUP_SUFFIX).exists())
        assertFalse(File(database.path + EncryptionMigrator.COPY_SUFFIX).exists())
    }

    @Test
    fun `a copy with missing rows is thrown away and the plain database kept`() {
        createPlainDatabase()
        val before = database.readBytes()

        val outcome = EncryptionMigrator(FakeCipher(copyCounts = mapOf("chats" to 0))).migrate(database, key)

        assertEquals(MigrationOutcome.FAILED, outcome)
        assertArrayEquals(before, database.readBytes())
        assertFalse(File(database.path + EncryptionMigrator.COPY_SUFFIX).exists())
    }

    @Test
    fun `an export error keeps the plain database and records why`() {
        createPlainDatabase()
        val migrator = EncryptionMigrator(FakeCipher(failExport = true))

        assertEquals(MigrationOutcome.FAILED, migrator.migrate(database, key))
        assertTrue(SqliteHeader.isPlaintext(database))
        assertEquals("export failed", migrator.lastError?.message)
    }

    @Test
    fun `nothing happens for a new install or an already encrypted database`() {
        assertEquals(MigrationOutcome.NOTHING_TO_DO, EncryptionMigrator(FakeCipher()).migrate(database, key))
        database.writeBytes(ByteArray(4096) { 3 })
        assertEquals(MigrationOutcome.NOTHING_TO_DO, EncryptionMigrator(FakeCipher()).migrate(database, key))
    }

    @Test
    fun `a swap interrupted after moving the original aside is undone`() {
        createPlainDatabase(File(database.path + EncryptionMigrator.BACKUP_SUFFIX)) // the app died here

        val outcome = EncryptionMigrator(FakeCipher()).migrate(database, key)

        assertEquals(MigrationOutcome.MIGRATED, outcome) // the original came back and was then migrated
        assertFalse(File(database.path + EncryptionMigrator.BACKUP_SUFFIX).exists())
    }

    /** Reversible stand-in for the Keystore (which Robolectric doesn't have). */
    private class XorWrapper : KeyWrapper {
        var unwraps = 0

        override fun wrap(secret: ByteArray) = secret.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()

        override fun unwrap(wrapped: ByteArray) = wrap(wrapped).also { unwraps++ }
    }

    @Test
    fun `the key is created once, stored only wrapped, and the same after a restart`() {
        val keyFile = File(dir, "database.key")
        val wrapper = XorWrapper()

        val first = WrappedDatabasePassphrase(keyFile, wrapper).get()
        val again = WrappedDatabasePassphrase(keyFile, wrapper).get() // a new app start

        assertEquals(64, first.size) // 32 random bytes as hex
        assertTrue(String(first).all { it in "0123456789abcdef" })
        assertArrayEquals(first, again)
        assertNotEquals(String(first), String(keyFile.readBytes())) // never stored in the clear
        assertEquals(1, wrapper.unwraps)
        assertNotEquals(String(first), String(WrappedDatabasePassphrase(File(dir, "other.key"), wrapper).get()))
    }

    private fun configuration(name: String?) = SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(name)
        .callback(
            object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    Unit
            },
        )
        .build()

    private fun factory(outcome: MigrationOutcome, choices: MutableList<String>) = EncryptedOpenHelperFactory(
        passphrase = { key },
        migrate = { _, _ -> outcome.also { choices += "migrate" } },
        encrypted = { passphrase ->
            assertArrayEquals(key, passphrase)
            choices += "encrypted"
            FrameworkSQLiteOpenHelperFactory() // stands in for SQLCipher's factory
        },
        plain = SupportSQLiteOpenHelper.Factory { configuration ->
            choices += "plain"
            FrameworkSQLiteOpenHelperFactory().create(configuration)
        },
        databaseFile = { File(dir, it) },
    )

    @Test
    fun `the helper is chosen on first access, not when it is created`() {
        val choices = mutableListOf<String>()
        val helper = factory(MigrationOutcome.MIGRATED, choices).create(configuration("chosen.db"))
        helper.setWriteAheadLoggingEnabled(true)
        assertTrue(choices.isEmpty()) // nothing decided at app start

        helper.writableDatabase.execSQL("CREATE TABLE t (x INTEGER)")
        helper.readableDatabase.query("SELECT 1").close()
        helper.close()

        assertEquals(listOf("migrate", "encrypted"), choices)
        assertEquals("chosen.db", helper.databaseName)
    }

    @Test
    fun `a failed migration falls back to the plain file, and in-memory databases stay plain`() {
        val choices = mutableListOf<String>()
        factory(MigrationOutcome.FAILED, choices).create(configuration("fallback.db")).writableDatabase.close()
        assertEquals(listOf("migrate", "plain"), choices)

        choices.clear()
        factory(MigrationOutcome.MIGRATED, choices).create(configuration(null)).writableDatabase.close()
        assertEquals(listOf("plain"), choices)
    }
}
