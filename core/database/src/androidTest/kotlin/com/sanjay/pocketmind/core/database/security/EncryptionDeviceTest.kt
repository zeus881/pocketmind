package com.sanjay.pocketmind.core.database.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import java.io.File
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real SQLCipher and Android Keystore, on a phone: `./gradlew :core:database:connectedDebugAndroidTest`.
 * (Robolectric can't run SQLCipher's native library or the Keystore.)
 */
@RunWith(AndroidJUnit4::class)
class EncryptionDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun keystoreWrapsAndUnwraps() {
        val wrapper = KeystoreKeyWrapper(alias = "pocketmind.test")
        val secret = "0123456789abcdef".toByteArray()

        val wrapped = wrapper.wrap(secret)

        assertFalse(String(wrapped).contains("0123456789abcdef"))
        assertArrayEquals(secret, wrapper.unwrap(wrapped))
    }

    @Test
    fun aPlainRoomDatabaseIsMigratedAndOpensEncrypted() = runTest {
        val name = "encryption-device-test.db"
        context.deleteDatabase(name)
        // A plain v-current database, as an older PocketMind version left it.
        Room.databaseBuilder(context, PocketMindDatabase::class.java, name).build().apply {
            openHelper.writableDatabase.execSQL(
                "INSERT INTO chats (id, title, createdAt, updatedAt) VALUES ('c1', 'Old chat', 1, 1)",
            )
            close()
        }
        val file = context.getDatabasePath(name)
        assertTrue(SqliteHeader.isPlaintext(file))
        val keyFile = File(context.noBackupFilesDir, "test.key")
        val key = WrappedDatabasePassphrase(keyFile, KeystoreKeyWrapper("pocketmind.test")).get()

        val migrator = EncryptionMigrator(SqlCipherOperations())
        val outcome = migrator.migrate(file, key)
        val why = migrator.lastError?.stackTraceToString()?.take(ERROR_CHARS)
        assertEquals("migration error: $why", MigrationOutcome.MIGRATED, outcome)
        assertFalse(SqliteHeader.isPlaintext(file))

        val encrypted = Room.databaseBuilder(context, PocketMindDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
        assertEquals("Old chat", encrypted.chatDao().get("c1")?.title)
        encrypted.close()

        // Without the key the file can't be read.
        val wrongKey = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT COUNT(*) FROM chats", null).close()
            }
        }
        assertTrue(wrongKey.isFailure)
        context.deleteDatabase(name)
    }

    private companion object {
        const val ERROR_CHARS = 3000
    }
}
