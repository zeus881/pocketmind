package com.manjit.pocketmind.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manjit.pocketmind.core.database.dao.ChatDao
import com.manjit.pocketmind.core.database.dao.MessageDao
import com.manjit.pocketmind.core.database.entity.ChatEntity
import com.manjit.pocketmind.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DAO tests against a REAL SQLite database kept in memory (fast, nothing written to disk).
 * Robolectric provides the Android classes (Context, SQLite) on the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
class ChatDaoTest {
    private lateinit var db: PocketMindDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatDao = db.chatDao()
        messageDao = db.messageDao()
    }

    @After
    fun tearDown() = db.close()

    private fun chat(id: String, title: String, updatedAt: Long) =
        ChatEntity(id, title, createdAt = 0, updatedAt = updatedAt)

    private fun message(id: String, chatId: String, text: String, at: Long) =
        MessageEntity(id, chatId, "USER", text, at, "COMPLETE", null, null)

    @Test
    fun summariesAreNewestFirstWithLastMessagePreview() = runTest {
        chatDao.upsert(chat("a", "Old chat", updatedAt = 1))
        chatDao.upsert(chat("b", "New chat", updatedAt = 2))
        messageDao.upsert(message("m1", "a", "first", at = 1))
        messageDao.upsert(message("m2", "a", "second", at = 2))

        val rows = chatDao.observeSummaries("").first()

        assertEquals(listOf("b", "a"), rows.map { it.id })
        assertEquals("second", rows[1].lastMessage)
        assertNull(rows[0].lastMessage)
    }

    @Test
    fun searchMatchesTitleOrMessageText() = runTest {
        chatDao.upsert(chat("a", "Drone batteries", updatedAt = 1))
        chatDao.upsert(chat("b", "Cooking", updatedAt = 2))
        messageDao.upsert(message("m1", "b", "How long do I boil eggs for a drone pilot?", at = 1))
        chatDao.upsert(chat("c", "Taxes", updatedAt = 3))

        val rows = chatDao.observeSummaries("drone").first()

        assertEquals(setOf("a", "b"), rows.map { it.id }.toSet())
    }

    @Test
    fun deletingChatCascadesToMessages() = runTest {
        chatDao.upsert(chat("a", "Chat", updatedAt = 1))
        messageDao.upsert(message("m1", "a", "hello", at = 1))

        chatDao.delete("a")

        assertTrue(messageDao.getAll("a").isEmpty())
    }

    @Test
    fun restoreBringsBackChatAndMessages() = runTest {
        val c = chat("a", "Chat", updatedAt = 1)
        val messages = listOf(message("m1", "a", "hello", 1), message("m2", "a", "world", 2))
        chatDao.restore(c, messages)

        assertEquals(c, chatDao.get("a"))
        assertEquals(listOf("m1", "m2"), messageDao.getAll("a").map { it.id })
    }

    @Test
    fun renameUpdatesTitleAndTimestamp() = runTest {
        chatDao.upsert(chat("a", "Old", updatedAt = 1))
        chatDao.rename("a", "New", updatedAt = 5)

        val renamed = chatDao.get("a")!!
        assertEquals("New", renamed.title)
        assertEquals(5, renamed.updatedAt)
    }
}
