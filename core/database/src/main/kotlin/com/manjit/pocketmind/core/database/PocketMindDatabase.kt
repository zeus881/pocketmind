package com.manjit.pocketmind.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.manjit.pocketmind.core.database.dao.ChatDao
import com.manjit.pocketmind.core.database.dao.MessageDao
import com.manjit.pocketmind.core.database.entity.ChatEntity
import com.manjit.pocketmind.core.database.entity.MessageEntity

/**
 * The app's SQLite database, described for Room.
 *
 * - `version` must be increased whenever a table changes, together with a Migration that
 *   converts old data. Room refuses to open a database whose schema doesn't match.
 * - `exportSchema = true` writes `schemas/<version>.json`, which we commit and use in tests.
 *
 * Room is the app's SOURCE OF TRUTH: the UI shows what is in these tables, never a copy.
 */
@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "pocketmind.db"
    }
}
