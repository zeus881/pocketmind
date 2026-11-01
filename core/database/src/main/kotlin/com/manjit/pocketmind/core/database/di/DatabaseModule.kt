package com.manjit.pocketmind.core.database.di

import android.content.Context
import androidx.room.Room
import com.manjit.pocketmind.core.database.PocketMindDatabase
import com.manjit.pocketmind.core.database.dao.BenchmarkDao
import com.manjit.pocketmind.core.database.dao.ChatDao
import com.manjit.pocketmind.core.database.dao.MessageDao
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
            .build()

    @Provides
    fun providesChatDao(db: PocketMindDatabase): ChatDao = db.chatDao()

    @Provides
    fun providesMessageDao(db: PocketMindDatabase): MessageDao = db.messageDao()

    @Provides
    fun providesBenchmarkDao(db: PocketMindDatabase): BenchmarkDao = db.benchmarkDao()
}
