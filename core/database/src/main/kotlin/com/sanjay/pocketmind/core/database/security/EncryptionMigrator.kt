package com.sanjay.pocketmind.core.database.security

import android.database.SQLException
import java.io.File
import java.io.IOException

/** What [EncryptionMigrator.migrate] did. */
enum class MigrationOutcome {
    /** No database yet, or it is encrypted already. */
    NOTHING_TO_DO,

    /** The plain database was converted and replaced by the encrypted copy. */
    MIGRATED,

    /** Something went wrong; the plain database is untouched and still in use. */
    FAILED,
}

/** The SQLCipher operations the migration needs, behind an interface so the steps are unit-tested. */
interface CipherOperations {
    /** Writes an encrypted copy of the plain database [plain] to [target] (`sqlcipher_export`). */
    fun exportEncrypted(plain: File, target: File, passphrase: ByteArray)

    /** Rows per table, to compare the copy with the original. [passphrase] null = a plain database. */
    fun rowCounts(database: File, passphrase: ByteArray?): Map<String, Long>
}

/**
 * Converts the existing, unencrypted chat database into an encrypted one, ONCE, without ever risking
 * the user's chats:
 *
 * 1. Export an encrypted copy next to it (`pocketmind.db.encrypting`).
 * 2. Open the copy with the key and compare the row count of every table with the original.
 * 3. Only then swap: original → `.plain-backup`, copy → `pocketmind.db`; then delete the backup and
 *    the old WAL/SHM files.
 *
 * A crash or failure before step 3 leaves the original as it was (the half-written copy is deleted on
 * the next try). After step 3 the app only ever opens the encrypted file.
 */
class EncryptionMigrator(private val operations: CipherOperations) {

    fun migrate(database: File, passphrase: ByteArray): MigrationOutcome {
        val copy = File(database.path + COPY_SUFFIX)
        val backup = File(database.path + BACKUP_SUFFIX)
        recoverInterruptedSwap(database, backup)
        copy.delete() // left over from an interrupted earlier attempt
        if (!database.exists() || !SqliteHeader.isPlaintext(database)) return MigrationOutcome.NOTHING_TO_DO

        val outcome = try {
            operations.exportEncrypted(database, copy, passphrase)
            val original = operations.rowCounts(database, passphrase = null)
            val encrypted = operations.rowCounts(copy, passphrase)
            if (original != encrypted || SqliteHeader.isPlaintext(copy)) {
                MigrationOutcome.FAILED
            } else {
                swap(database, copy, backup)
                MigrationOutcome.MIGRATED
            }
        } catch (e: IOException) {
            MigrationOutcome.FAILED.also { lastError = e }
        } catch (e: SQLException) { // SQLCipher reports database errors as android.database.SQLException
            MigrationOutcome.FAILED.also { lastError = e }
        } catch (e: IllegalStateException) {
            MigrationOutcome.FAILED.also { lastError = e }
        }
        if (outcome == MigrationOutcome.FAILED) copy.delete()
        return outcome
    }

    /** Why the last migration failed, for the log. */
    var lastError: Exception? = null
        private set

    private fun swap(database: File, copy: File, backup: File) {
        if (!database.renameTo(backup)) throw IOException("could not move the plain database aside")
        if (!copy.renameTo(database)) {
            backup.renameTo(database) // put the original back
            throw IOException("could not move the encrypted copy into place")
        }
        // The old journal files belong to the plain database; SQLCipher must not replay them.
        File(database.path + "-wal").delete()
        File(database.path + "-shm").delete()
        File(database.path + "-journal").delete()
        backup.delete()
    }

    /** The app died between the two renames: the backup is the only complete database, so restore it. */
    private fun recoverInterruptedSwap(database: File, backup: File) {
        if (backup.exists() && !database.exists()) backup.renameTo(database)
        if (backup.exists() && database.exists() && !SqliteHeader.isPlaintext(database)) backup.delete()
    }

    companion object {
        const val COPY_SUFFIX = ".encrypting"
        const val BACKUP_SUFFIX = ".plain-backup"
    }
}

/** Every unencrypted SQLite file starts with these 16 bytes; an encrypted one starts with random bytes. */
object SqliteHeader {
    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun isPlaintext(file: File): Boolean {
        if (file.length() < MAGIC.size) return false
        val start = ByteArray(MAGIC.size)
        file.inputStream().use { input -> if (input.read(start) != MAGIC.size) return false }
        return start.contentEquals(MAGIC)
    }
}
