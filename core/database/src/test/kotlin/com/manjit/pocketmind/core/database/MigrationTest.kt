package com.manjit.pocketmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves that an existing user's database survives an app update (v1 → v2).
 *
 * 1. Build a REAL version-1 database from the committed `schemas/.../1.json` — the exact SQL
 *    Room generated in phase 3, including Room's identity hash.
 * 2. Insert data the way a phase-3 user would have it.
 * 3. Open it with today's [PocketMindDatabase] (version 2). Room runs the AutoMigration and
 *    then VALIDATES every table against what the code expects; a wrong migration throws.
 * 4. Check the old chat is still there and the new table works.
 *
 * (Room's MigrationTestHelper does steps 1–3 too, but its SQLite driver rejects Robolectric's
 * Windows file paths, so we do it explicitly — which also shows what a migration really is.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @Test
    fun migrate1To2KeepsChatsAndAddsBenchmarkTable() = runTest {
        createVersion1Database()

        val db = Room.databaseBuilder(context, PocketMindDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val chats = db.chatDao().observeSummaries("").first() // opening triggers migrate + validate
        assertEquals(listOf("Old chat"), chats.map { it.title })
        assertEquals("hello", chats.single().lastMessage)
        assertTrue(db.benchmarkDao().observeAll().first().isEmpty())
        db.close()
    }

    private fun createVersion1Database() {
        val schema = Json.parseToJsonElement(File(SCHEMA_DIR, "1.json").readText()).jsonObject
        val database = schema.getValue("database").jsonObject
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.string("tableName")
                sqlite.execSQL(entity.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    sqlite.execSQL(index.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            // Room's bookkeeping table + the v1 identity hash, exactly as Room wrote them.
            database.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.version = 1
            sqlite.execSQL("INSERT INTO chats (id, title, createdAt, updatedAt) VALUES ('c1', 'Old chat', 1, 1)")
            sqlite.execSQL(
                "INSERT INTO messages (id, chatId, role, text, createdAt, status, tokensPerSecond, backend) " +
                    "VALUES ('m1', 'c1', 'USER', 'hello', 1, 'COMPLETE', NULL, NULL)",
            )
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private companion object {
        // Gradle runs unit tests with the module folder as the working directory.
        const val SCHEMA_DIR = "schemas/com.manjit.pocketmind.core.database.PocketMindDatabase"
    }
}
