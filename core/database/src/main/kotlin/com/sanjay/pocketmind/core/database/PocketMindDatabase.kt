package com.sanjay.pocketmind.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sanjay.pocketmind.core.database.dao.BenchmarkDao
import com.sanjay.pocketmind.core.database.dao.ChatDao
import com.sanjay.pocketmind.core.database.dao.DocumentDao
import com.sanjay.pocketmind.core.database.dao.FolderDao
import com.sanjay.pocketmind.core.database.dao.MessageDao
import com.sanjay.pocketmind.core.database.dao.NoteDao
import com.sanjay.pocketmind.core.database.dao.PersonaDao
import com.sanjay.pocketmind.core.database.entity.BenchmarkRunEntity
import com.sanjay.pocketmind.core.database.entity.ChatEntity
import com.sanjay.pocketmind.core.database.entity.ChunkEntity
import com.sanjay.pocketmind.core.database.entity.ConversationSummaryEntity
import com.sanjay.pocketmind.core.database.entity.DocumentEntity
import com.sanjay.pocketmind.core.database.entity.FolderEntity
import com.sanjay.pocketmind.core.database.entity.MessageEntity
import com.sanjay.pocketmind.core.database.entity.MessageFtsEntity
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import com.sanjay.pocketmind.core.database.entity.PersonaEntity
import com.sanjay.pocketmind.core.database.entity.PromptEntity

/**
 * The app's SQLite database, described for Room.
 *
 * Version history (every version's schema is committed in `schemas/`):
 * - 1 (phase 3): chats, messages
 * - 2 (phase 5): + benchmark_runs
 * - 3 (phase 7): + documents, chunks (RAG index)
 * - 4 (v2 phase 7): + conversation_summaries (cached summaries of long chats)
 * - 5 (v2 phase 11): chats.pinned/folderId/currentLeafId, messages.parentId, folders, messages_fts.
 *   A MANUAL migration ([Migrations.MIGRATION_4_5]): existing linear chats get their parent links
 *   and current branch filled in, and the search index is built — things Room can't infer.
 * - 6 (v2 phase 14): + personas, prompts, chats.personaId (AutoMigration: purely additive).
 * - 7 (v2 phase 16): + messages.imagePath (AutoMigration).
 * - 8 (v2 phase 17): + notes, chats.toolsEnabled (default 1), messages.toolsUsed (AutoMigration).
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
        FolderEntity::class,
        MessageFtsEntity::class,
        PersonaEntity::class,
        PromptEntity::class,
        NoteEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao

    abstract fun benchmarkDao(): BenchmarkDao

    abstract fun documentDao(): DocumentDao

    abstract fun folderDao(): FolderDao

    abstract fun personaDao(): PersonaDao

    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "pocketmind.db"
    }
}
