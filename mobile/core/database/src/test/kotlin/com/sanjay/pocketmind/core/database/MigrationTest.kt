package com.sanjay.pocketmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.database.entity.ConversationSummaryEntity
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves that existing users' databases survive app updates, from EVERY older version to the
 * current one (a user may skip updates and jump from v1 straight to the newest version).
 *
 * 1. Build a REAL old database from the committed `schemas/.../<version>.json` — the exact SQL
 *    Room generated back then, including Room's identity hash.
 * 2. Insert data the way an old version would have it.
 * 3. Open it with today's [PocketMindDatabase]. Room runs the AutoMigrations in order and then
 *    VALIDATES every table against what the code expects; a wrong migration throws.
 * 4. Check the old data is still there and the new tables work.
 *
 * (Room's MigrationTestHelper does steps 1–3 too, but its SQLite driver rejects Robolectric's
 * Windows file paths, so we do it explicitly — which also shows what a migration really is.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrateFrom1KeepsChats() = runTest { migrateAndCheck(fromVersion = 1) }

    @Test
    fun migrateFrom2KeepsChats() = runTest { migrateAndCheck(fromVersion = 2) }

    @Test
    fun migrateFrom3KeepsChats() = runTest { migrateAndCheck(fromVersion = 3) }

    @Test
    fun migrateFrom4KeepsChats() = runTest { migrateAndCheck(fromVersion = 4) }

    @Test
    fun migrateFrom5KeepsChats() = runTest { migrateAndCheck(fromVersion = 5) }

    @Test
    fun migrateFrom6KeepsChats() = runTest { migrateAndCheck(fromVersion = 6) }

    @Test
    fun migrateFrom7KeepsChats() = runTest { migrateAndCheck(fromVersion = 7) }

    /** v4 → v5 is a manual migration: check the data it derives, not just that it opens. */
    @Test
    fun migrateFrom4LinksMessagesSetsTheBranchAndIndexesText() = runTest {
        val name = "migration-v4-branches.db"
        createOldDatabase(name, 4) { sqlite ->
            sqlite.execSQL(
                "INSERT INTO messages (id, chatId, role, text, createdAt, status, tokensPerSecond, backend) " +
                    "VALUES ('m2', 'c1', 'ASSISTANT', 'Quadcopters use gyroscopes', 2, 'COMPLETE', NULL, NULL)",
            )
            sqlite.execSQL(
                "INSERT INTO messages (id, chatId, role, text, createdAt, status, tokensPerSecond, backend) " +
                    "VALUES ('m3', 'c1', 'USER', 'thanks', 3, 'COMPLETE', NULL, NULL)",
            )
        }
        val db = open(name)

        val messages = db.messageDao().getAll("c1")
        assertEquals(listOf(null, "m1", "m2"), messages.map { it.parentId })
        assertEquals("m3", db.chatDao().get("c1")?.currentLeafId)
        val found = db.chatDao().observeSummaries("gyro", "gyro", "\"gyro*\"").first()
        assertEquals(listOf("c1"), found.map { it.id })
        assertEquals("thanks", found.single().lastMessage)
        db.close()
    }

    private suspend fun migrateAndCheck(fromVersion: Int) {
        val name = "migration-from-$fromVersion.db"
        createOldDatabase(name, fromVersion)

        val db = open(name)
        val chats = db.chatDao().observeSummaries("", "", "").first() // opening triggers migrate + validate
        assertEquals(listOf("Old chat"), chats.map { it.title })
        assertEquals("hello", chats.single().lastMessage)
        assertTrue(db.benchmarkDao().observeAll().first().isEmpty())
        assertTrue(db.documentDao().observeAll().first().isEmpty())
        // v4 table exists and works on a migrated database.
        assertNull(db.chatDao().summary("c1"))
        db.chatDao().upsertSummary(ConversationSummaryEntity("c1", "Earlier: greetings.", 2, 5))
        assertEquals("Earlier: greetings.", db.chatDao().summary("c1")?.summary)
        // v7 column: old messages have no photo; a new one can store it.
        assertNull(db.messageDao().get("m1")?.imagePath)
        db.messageDao().upsert(checkNotNull(db.messageDao().get("m1")).copy(imagePath = "/photos/a.jpg"))
        assertEquals(listOf("/photos/a.jpg"), db.messageDao().imagePaths())
        // v8: existing chats may use tools; old answers used none; the notes table works.
        assertTrue(checkNotNull(db.chatDao().get("c1")).toolsEnabled)
        assertNull(db.messageDao().get("m1")?.toolsUsed)
        db.noteDao().insert(NoteEntity("n1", "Locker code 4312", 9))
        assertEquals(listOf("n1"), db.noteDao().search("locker", 5).map { it.id })
        db.close()
    }

    /** Opens with today's database class and the same migrations the app uses. */
    private fun open(name: String) = Room.databaseBuilder(context, PocketMindDatabase::class.java, name)
        .apply { Migrations.ALL.forEach { addMigrations(it) } }
        .allowMainThreadQueries()
        .build()

    private fun createOldDatabase(name: String, version: Int, extra: (SQLiteDatabase) -> Unit = {}) {
        val schema = Json.parseToJsonElement(File(SCHEMA_DIR, "$version.json").readText()).jsonObject
        val database = schema.getValue("database").jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.string("tableName")
                sqlite.execSQL(entity.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    sqlite.execSQL(index.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
                }
                // FTS tables (v5+) come with the triggers that keep them in sync.
                entity.jsonObject["contentSyncTriggers"]?.jsonArray?.forEach {
                    sqlite.execSQL(it.jsonPrimitive.content)
                }
            }
            // Room's bookkeeping table + that version's identity hash, exactly as Room wrote them.
            database.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.version = version
            sqlite.execSQL("INSERT INTO chats (id, title, createdAt, updatedAt) VALUES ('c1', 'Old chat', 1, 1)")
            sqlite.execSQL(
                "INSERT INTO messages (id, chatId, role, text, createdAt, status, tokensPerSecond, backend) " +
                    "VALUES ('m1', 'c1', 'USER', 'hello', 1, 'COMPLETE', NULL, NULL)",
            )
            extra(sqlite)
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private companion object {
        // Gradle runs unit tests with the module folder as the working directory.
        const val SCHEMA_DIR = "schemas/com.sanjay.pocketmind.core.database.PocketMindDatabase"
    }
}
