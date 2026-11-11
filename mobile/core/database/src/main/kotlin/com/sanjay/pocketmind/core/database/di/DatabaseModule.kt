package com.sanjay.pocketmind.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.sanjay.pocketmind.core.database.Migrations
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.database.UserDataStore
import com.sanjay.pocketmind.core.database.security.EncryptedOpenHelperFactory
import com.sanjay.pocketmind.core.database.security.EncryptionMigrator
import com.sanjay.pocketmind.core.database.security.KeystoreKeyWrapper
import com.sanjay.pocketmind.core.database.security.SqlCipherOperations
import com.sanjay.pocketmind.core.database.security.WrappedDatabasePassphrase
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber

/**
 * The DAOs and the data-export helper, all read from the ONE database (V3: Koin; v2 used Hilt).
 * DAOs are cheap views of the database, so `factory` is enough.
 */
val databaseModule = module {
    factory { get<PocketMindDatabase>().chatDao() }
    factory { get<PocketMindDatabase>().messageDao() }
    factory { get<PocketMindDatabase>().benchmarkDao() }
    factory { get<PocketMindDatabase>().documentDao() }
    factory { get<PocketMindDatabase>().folderDao() }
    factory { get<PocketMindDatabase>().personaDao() }
    factory { get<PocketMindDatabase>().noteDao() }
    factory { UserDataStore(get()) }
}

/**
 * The database FILE (encrypted, on the phone): ONE instance for the whole app, because opening SQLite is
 * expensive. Separate from [databaseModule] so end-to-end tests can swap in an in-memory database and keep
 * everything else real.
 */
val databaseFileModule = module {
    single { openDatabase(androidContext()) }
}

private fun openDatabase(context: Context): PocketMindDatabase =
    Room.databaseBuilder(context, PocketMindDatabase::class.java, PocketMindDatabase.NAME)
        .apply { Migrations.ALL.forEach { addMigrations(it) } }
        // v2 phase 18: chats and notes are encrypted at rest (SQLCipher, key wrapped by Android Keystore).
        .openHelperFactory(encryptedOpenHelperFactory(context))
        .build()

private fun encryptedOpenHelperFactory(context: Context) = EncryptedOpenHelperFactory(
    passphrase = WrappedDatabasePassphrase(File(context.noBackupFilesDir, KEY_FILE), KeystoreKeyWrapper()),
    migrate = { file, key ->
        val migrator = EncryptionMigrator(SqlCipherOperations())
        migrator.migrate(file, key).also { outcome ->
            Timber.i("Database encryption: %s", outcome)
            migrator.lastError?.let { Timber.e(it, "Database encryption failed; using the plain file") }
        }
    },
    encrypted = { key -> SupportOpenHelperFactory(key) },
    plain = FrameworkSQLiteOpenHelperFactory(),
    databaseFile = context::getDatabasePath,
)

/** The wrapped database key; in noBackupFilesDir so no backup or transfer ever carries it. */
private const val KEY_FILE = "database.key"
