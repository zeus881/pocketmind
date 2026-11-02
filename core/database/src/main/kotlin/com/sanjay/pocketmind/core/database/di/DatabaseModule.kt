package com.sanjay.pocketmind.core.database.di

import android.content.Context
import androidx.room.Room
import com.sanjay.pocketmind.core.database.Migrations
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.database.dao.BenchmarkDao
import com.sanjay.pocketmind.core.database.dao.ChatDao
import com.sanjay.pocketmind.core.database.dao.DocumentDao
import com.sanjay.pocketmind.core.database.dao.FolderDao
import com.sanjay.pocketmind.core.database.dao.MessageDao
import com.sanjay.pocketmind.core.database.dao.PersonaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides ONE database instance for the whole app (opening SQLite is expensive). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): PocketMindDatabase =
        Room.databaseBuilder(context, PocketMindDatabase::class.java, PocketMindDatabase.NAME)
            .apply { Migrations.ALL.forEach { addMigrations(it) } }
            .build()

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
}
