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
        // An empty password opens an ordinary, unencrypted SQLite file. CREATE_IF_NECESSARY is required even
        // though this file exists: an ATTACHed database is opened with the main connection's flags, and without
        // it SQLite may not create the encrypted copy (found on the phone: "unable to open database", code 14).
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        val database = SQLiteDatabase.openDatabase(plain.path, "", null, flags, null)
        try {
            val key = String(passphrase, Charsets.UTF_8) // hex digits only: safe inside the SQL literal
            // One connection for the whole export (WAL mode uses a pool); this also checkpoints the WAL file
            // into the main file, so the export sees every row.
            database.disableWriteAheadLogging()
            val version = database.version
            // SQLCipher's documented export: ATTACH, sqlcipher_export, DETACH. execSQL (not rawExecSQL) for the
            // ATTACH and DETACH, because rawExecSQL hid the ATTACH failure; rawQuery + reading the row makes sure
            // the export statement really runs.
            database.execSQL("ATTACH DATABASE '${target.path.replace("'", "''")}' AS encrypted KEY '$key'")
            database.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { it.moveToFirst() }
            database.execSQL("DETACH DATABASE encrypted")
            // sqlcipher_export copies tables, indexes and triggers, but not the schema version Room checks:
            // set it on the encrypted copy itself, opened with its key.
            val copy = SQLiteDatabase.openDatabase(target.path, key, null, SQLiteDatabase.OPEN_READWRITE, null)
            try {
                copy.version = version
            } finally {
                copy.close()
            }
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
