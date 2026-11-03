package com.sanjay.pocketmind.core.database.security

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File

/**
 * Room's open helper for the chat database. The decision "encrypted or not" is made on the FIRST
 * real database access, not when the app starts: Room does that access on a background thread,
 * which is where the one-time migration (seconds for a big history) must run.
 *
 * - Encrypted database, or none yet → open/create it with the key (SQLCipher).
 * - Plain database (installed before v2 phase 18) → migrate it first ([EncryptionMigrator]).
 * - Migration failed → keep using the plain database this time; the next start tries again.
 */
class EncryptedOpenHelperFactory(
    private val passphrase: DatabasePassphrase,
    private val migrate: (database: File, passphrase: ByteArray) -> MigrationOutcome,
    private val encrypted: (ByteArray) -> SupportSQLiteOpenHelper.Factory,
    private val plain: SupportSQLiteOpenHelper.Factory,
    private val databaseFile: (name: String) -> File,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredOpenHelper(configuration) { choose(configuration) }

    private fun choose(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val name = configuration.name ?: return plain.create(configuration) // in-memory: nothing to protect
        val key = passphrase.get()
        return when (migrate(databaseFile(name), key)) {
            MigrationOutcome.NOTHING_TO_DO, MigrationOutcome.MIGRATED -> encrypted(key).create(configuration)
            MigrationOutcome.FAILED -> plain.create(configuration)
        }
    }
}

/** Creates the real helper on first use; remembers settings Room applies before that. */
internal class DeferredOpenHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val factory: () -> SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper {
    private var walEnabled: Boolean? = null
    private var helper: SupportSQLiteOpenHelper? = null

    @Synchronized
    private fun helper(): SupportSQLiteOpenHelper = helper ?: factory().also { created ->
        walEnabled?.let(created::setWriteAheadLoggingEnabled)
        helper = created
    }

    override val databaseName: String? get() = configuration.name

    @Synchronized
    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        walEnabled = enabled
        helper?.setWriteAheadLoggingEnabled(enabled)
    }

    override val writableDatabase: SupportSQLiteDatabase get() = helper().writableDatabase

    override val readableDatabase: SupportSQLiteDatabase get() = helper().readableDatabase

    @Synchronized
    override fun close() {
        helper?.close()
    }
}
