package com.manjit.pocketmind.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.manjit.pocketmind.core.database.dao.BenchmarkDao
import com.manjit.pocketmind.core.database.dao.ChatDao
import com.manjit.pocketmind.core.database.dao.MessageDao
import com.manjit.pocketmind.core.database.entity.BenchmarkRunEntity
import com.manjit.pocketmind.core.database.entity.ChatEntity
import com.manjit.pocketmind.core.database.entity.MessageEntity

/**
 * The app's SQLite database, described for Room.
 *
 * Version history (every version's schema is committed in `schemas/`):
 * - 1 (phase 3): chats, messages
 * - 2 (phase 5): + benchmark_runs — an AutoMigration: Room compares schemas/1.json with
 *   schemas/2.json at compile time and generates the `CREATE TABLE` itself. Existing users keep
 *   all their chats; [MigrationTest] proves it.
 *
 * Room is the app's SOURCE OF TRUTH: the UI shows what is in these tables, never a copy.
 */
@Database(
    entities = [ChatEntity::class, MessageEntity::class, BenchmarkRunEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao

    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        const val NAME = "pocketmind.db"
    }
}
