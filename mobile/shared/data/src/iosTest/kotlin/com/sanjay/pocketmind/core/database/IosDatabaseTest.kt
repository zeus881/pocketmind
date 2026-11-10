package com.sanjay.pocketmind.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sanjay.pocketmind.core.database.entity.ChatEntity
import com.sanjay.pocketmind.core.database.entity.MessageEntity
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The shared schema on iOS (V3): Room's Kotlin/Native code with the bundled SQLite driver, the setup the iOS
 * app uses. Runs on the macOS CI runner (`:shared:data:iosSimulatorArm64Test`).
 *
 * Above all it answers AUDIT_V3 risk D-6: v2's history search is an FTS4 table (`messages_fts`, tokenizer
 * unicode61) kept in sync by triggers. If the bundled SQLite were built without FTS4, creating the database
 * would already fail here.
 */
class IosDatabaseTest {
    private lateinit var db: PocketMindDatabase

    @BeforeTest
    fun open() {
        db = Room.inMemoryDatabaseBuilder<PocketMindDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @AfterTest
    fun close() = db.close()

    @Test
    fun fullTextSearchFollowsInsertsAndDeletes() = runTest {
        db.chatDao().upsert(ChatEntity("c1", "Untitled", createdAt = 1, updatedAt = 1))
        db.messageDao().upsert(message("m1", "c1", "Propeller balancing"))

        assertEquals(listOf("c1"), search("prop"))

        db.messageDao().delete("m1")
        assertEquals(emptyList(), search("prop"))
    }

    @Test
    fun hindiTextIsSearchable() = runTest {
        // unicode61 must split Devanagari into words the same way on iOS as on Android.
        db.chatDao().upsert(ChatEntity("c2", "Untitled", createdAt = 1, updatedAt = 1))
        db.messageDao().upsert(message("m2", "c2", "मौसम आज बहुत अच्छा है"))

        assertEquals(listOf("c2"), search("मौसम"))
    }

    @Test
    fun notesAreSavedAndFound() = runTest {
        db.noteDao().insert(NoteEntity("n1", "Locker code 4312", 9))

        assertEquals(listOf("n1"), db.noteDao().search("locker", 5).map { it.id })
    }

    /** Same arguments data:chat's SearchQuery builds for a plain word: LIKE pattern + FTS prefix query. */
    private suspend fun search(word: String) =
        db.chatDao().observeSummaries(word, word, "\"$word*\"").first().map { it.id }

    private fun message(id: String, chatId: String, text: String) = MessageEntity(
        id,
        chatId,
        "USER",
        text,
        createdAt = 1,
        status = "COMPLETE",
        tokensPerSecond = null,
        backend = null,
    )
}
