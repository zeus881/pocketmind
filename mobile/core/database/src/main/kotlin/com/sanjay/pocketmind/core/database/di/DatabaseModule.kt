package com.sanjay.pocketmind.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.sanjay.pocketmind.core.database.Migrations
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.database.dao.BenchmarkDao
import com.sanjay.pocketmind.core.database.dao.ChatDao
import com.sanjay.pocketmind.core.database.dao.DocumentDao
import com.sanjay.pocketmind.core.database.dao.FolderDao
import com.sanjay.pocketmind.core.database.dao.MessageDao
import com.sanjay.pocketmind.core.database.dao.NoteDao
import com.sanjay.pocketmind.core.database.dao.PersonaDao
import com.sanjay.pocketmind.core.database.security.EncryptedOpenHelperFactory
import com.sanjay.pocketmind.core.database.security.EncryptionMigrator
import com.sanjay.pocketmind.core.database.security.KeystoreKeyWrapper
import com.sanjay.pocketmind.core.database.security.SqlCipherOperations
import com.sanjay.pocketmind.core.database.security.WrappedDatabasePassphrase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber

/** Provides ONE database instance for the whole app (opening SQLite is expensive). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    fun providesChatDao(db: PocketMindDatabase): ChatDao = db.chatDao()

    @Provides
    fun providesMessageDao(db: PocketMindDatabase): MessageDao = db.messageDao()

    @Provides
    fun providesBenchmarkDao(db: PocketMindDatabase): BenchmarkDao = db.benchmarkDao()

    @Provides
    fun providesDocumentDao(db: PocketMindDatabase): DocumentDao = db.documentDao()

    @Provides
    fun providesFolderDao(db: PocketMindDatabase): FolderDao = db.folderDao()

    @Provides
    fun providesPersonaDao(db: PocketMindDatabase): PersonaDao = db.personaDao()

    @Provides
    fun providesNoteDao(db: PocketMindDatabase): NoteDao = db.noteDao()
}

/**
 * The database FILE (encrypted, on the phone). Separate from the DAO bindings so end-to-end tests can swap in
 * an in-memory database and keep everything else real.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseFileModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): PocketMindDatabase =
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
}
