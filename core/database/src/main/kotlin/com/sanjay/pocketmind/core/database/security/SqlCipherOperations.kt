package com.sanjay.pocketmind.core.database.security

import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * [CipherOperations] with the real SQLCipher library (native code, so it runs on a phone only; see
 * `EncryptionDeviceTest`).
 */
internal class SqlCipherOperations : CipherOperations {
    init {
        SqlCipherLibrary.load()
    }

    override fun exportEncrypted(plain: File, target: File, passphrase: ByteArray) {
        // An empty password opens an ordinary, unencrypted SQLite file.
        val database = SQLiteDatabase.openDatabase(plain.path, "", null, SQLiteDatabase.OPEN_READWRITE, null)
        try {
            val key = String(passphrase, Charsets.UTF_8) // hex digits only: safe inside the SQL literal
            database.rawExecSQL("ATTACH DATABASE '${target.path.replace("'", "''")}' AS encrypted KEY '$key'")
            database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            // sqlcipher_export copies tables, indexes and triggers, but not the schema version Room checks.
            database.execSQL("PRAGMA encrypted.user_version = ${database.version}")
            database.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            database.close()
        }
    }

    override fun rowCounts(database: File, passphrase: ByteArray?): Map<String, Long> {
        val password = passphrase?.let { String(it, Charsets.UTF_8) }.orEmpty()
        val db = SQLiteDatabase.openDatabase(database.path, password, null, SQLiteDatabase.OPEN_READONLY, null)
        try {
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                emptyArray<String>(),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            return tables.associateWith { table ->
                db.rawQuery("SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", emptyArray<String>())
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            }
        } finally {
            db.close()
        }
    }
}

/** The native library must be loaded once before any SQLCipher class is used. */
internal object SqlCipherLibrary {
    @Volatile private var loaded = false

    @Synchronized
    fun load() {
        if (!loaded) {
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}
