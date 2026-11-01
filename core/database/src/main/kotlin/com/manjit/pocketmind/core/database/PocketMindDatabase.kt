package com.manjit.pocketmind.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.manjit.pocketmind.core.database.dao.BenchmarkDao
import com.manjit.pocketmind.core.database.dao.ChatDao
import com.manjit.pocketmind.core.database.dao.DocumentDao
import com.manjit.pocketmind.core.database.dao.MessageDao
import com.manjit.pocketmind.core.database.entity.BenchmarkRunEntity
import com.manjit.pocketmind.core.database.entity.ChatEntity
import com.manjit.pocketmind.core.database.entity.ChunkEntity
import com.manjit.pocketmind.core.database.entity.DocumentEntity
import com.manjit.pocketmind.core.database.entity.MessageEntity

/**
 * The app's SQLite database, described for Room.
 *
 * Version history (every version's schema is committed in `schemas/`):
 * - 1 (phase 3): chats, messages
 * - 2 (phase 5): + benchmark_runs
 * - 3 (phase 7): + documents, chunks (RAG index)
 * Additive changes use AutoMigrations: Room diffs the committed schema JSONs at compile time
 * and generates the SQL. Existing users keep all their data; [MigrationTest] proves it.
 *
 * Room is the app's SOURCE OF TRUTH: the UI shows what is in these tables, never a copy.
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        BenchmarkRunEntity::class,
        DocumentEntity::class,
        ChunkEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao

    abstract fun benchmarkDao(): BenchmarkDao

    abstract fun documentDao(): DocumentDao

    companion object {
        const val NAME = "pocketmind.db"
    }
}
