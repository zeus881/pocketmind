package com.sanjay.pocketmind.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Hand-written migrations, for changes Room can't work out from the schema diff alone.
 *
 * Every statement is plain SQL that runs inside one transaction: if any line fails, SQLite rolls
 * the whole migration back and the user keeps their v4 database untouched. [MigrationTest] runs
 * it against real v1–v4 databases built from the committed schema files.
 *
 * V3: written against `SQLiteConnection` (multiplatform) instead of Android's `SupportSQLiteDatabase`, so the same
 * migration runs on iOS. On Android, Room calls `migrate(SQLiteConnection)` from its open-helper path too
 * (checked in the room-runtime 2.8.5 bytecode: RoomOpenHelper), so SQLCipher keeps working.
 */
object Migrations {

    /**
     * v4 → v5: pins, folders, message branches and full-text search.
     *
     * The structure (columns, table, indices, FTS table, triggers) is exactly what Room generated
     * in `schemas/…/5.json`; Room checks that after migrating and refuses to open the database if
     * anything differs. The DATA part is why this migration is manual:
     * - every existing message gets `parentId` = the message before it in its chat (v4 chats were
     *   straight lines, so the tree is a chain);
     * - every chat gets `currentLeafId` = its newest message (the branch that was being shown);
     * - the search index is filled from all existing messages (`'rebuild'`).
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            // Structure — chats and messages only GAIN columns (no table rebuild, no cascade risk).
            connection.execSQL("ALTER TABLE `chats` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE `chats` ADD COLUMN `folderId` TEXT")
            connection.execSQL("ALTER TABLE `chats` ADD COLUMN `currentLeafId` TEXT")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_chats_folderId` ON `chats` (`folderId`)")
            connection.execSQL("ALTER TABLE `messages` ADD COLUMN `parentId` TEXT")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_parentId` ON `messages` (`parentId`)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )

            // Data — link each message to the one before it (same order the app always used:
            // createdAt, then id), and point each chat at its newest message.
            connection.execSQL(
                """
                UPDATE messages SET parentId = (
                    SELECT p.id FROM messages p
                    WHERE p.chatId = messages.chatId
                      AND (p.createdAt < messages.createdAt OR (p.createdAt = messages.createdAt AND p.id < messages.id))
                    ORDER BY p.createdAt DESC, p.id DESC
                    LIMIT 1
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                UPDATE chats SET currentLeafId = (
                    SELECT m.id FROM messages m WHERE m.chatId = chats.id
                    ORDER BY m.createdAt DESC, m.id DESC
                    LIMIT 1
                )
                """.trimIndent(),
            )

            // Full-text search: the FTS table, the triggers that keep it in sync, then index
            // everything that already exists.
            connection.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(`text` TEXT NOT NULL, " +
                    "tokenize=unicode61, content=`messages`)",
            )
            FTS_TRIGGERS.forEach { connection.execSQL(it) }
            connection.execSQL("INSERT INTO `messages_fts`(`messages_fts`) VALUES('rebuild')")
        }
    }

    private val FTS_TRIGGERS = listOf(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_UPDATE BEFORE UPDATE ON `messages` " +
            "BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_DELETE BEFORE DELETE ON `messages` " +
            "BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_UPDATE AFTER UPDATE ON `messages` " +
            "BEGIN INSERT INTO `messages_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_INSERT AFTER INSERT ON `messages` " +
            "BEGIN INSERT INTO `messages_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
    )

    /** Every manual migration, in order. Used by the app's database builder AND by the tests. */
    val ALL: List<Migration> = listOf(MIGRATION_4_5)
}
