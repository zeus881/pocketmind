package com.sanjay.pocketmind.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sanjay.pocketmind.core.database.dao.BenchmarkDao
import com.sanjay.pocketmind.core.database.dao.ChatDao
import com.sanjay.pocketmind.core.database.dao.DocumentDao
import com.sanjay.pocketmind.core.database.dao.MessageDao
import com.sanjay.pocketmind.core.database.entity.BenchmarkRunEntity
import com.sanjay.pocketmind.core.database.entity.ChatEntity
import com.sanjay.pocketmind.core.database.entity.ChunkEntity
import com.sanjay.pocketmind.core.database.entity.ConversationSummaryEntity
import com.sanjay.pocketmind.core.database.entity.DocumentEntity
import com.sanjay.pocketmind.core.database.entity.MessageEntity

/**
 * The app's SQLite database, described for Room.
 *
 * Version history (every version's schema is committed in `schemas/`):
 * - 1 (phase 3): chats, messages
 * - 2 (phase 5): + benchmark_runs
 * - 3 (phase 7): + documents, chunks (RAG index)
 * - 4 (v2 phase 7): + conversation_summaries (cached summaries of long chats)
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
        ConversationSummaryEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
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
