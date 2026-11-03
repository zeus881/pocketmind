package com.sanjay.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sanjay.pocketmind.core.database.entity.ChatEntity
import com.sanjay.pocketmind.core.database.entity.ConversationSummaryEntity
import com.sanjay.pocketmind.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** A history-list row: chat columns plus the text of the message shown last (computed by SQL). */
data class ChatSummaryRow(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val folderId: String?,
    val currentLeafId: String?,
    val lastMessage: String?,
)

/**
 * Data Access Object for chats. Room generates the implementation at compile time and
 * checks every SQL string against the schema — a typo in a column name is a BUILD error.
 *
 * Functions returning `Flow` are "observable queries": Room re-runs them and emits a new list
 * whenever the tables they read change. That is what keeps the history screen live.
 */
@Dao
interface ChatDao {
    /**
     * History list, pinned chats first, then newest first. With a non-empty [query] only chats
     * whose title contains [likePattern] (an escaped LIKE pattern) or whose messages match the
     * full-text expression [ftsQuery] are returned. Build both with `SearchQuery` (data:chat).
     * The last-message preview follows the chat's CURRENT branch ([ChatEntity.currentLeafId]).
     */
    @Query(
        """
        SELECT c.id, c.title, c.createdAt, c.updatedAt, c.pinned, c.folderId, c.currentLeafId,
               COALESCE(
                   (SELECT m.text FROM messages m WHERE m.id = c.currentLeafId),
                   (SELECT m.text FROM messages m WHERE m.chatId = c.id ORDER BY m.createdAt DESC, m.id DESC LIMIT 1)
               ) AS lastMessage
        FROM chats c
        WHERE :query = ''
           OR c.title LIKE '%' || :likePattern || '%' ESCAPE '\'
           OR (:ftsQuery != '' AND c.id IN (
                SELECT m2.chatId FROM messages m2
                JOIN messages_fts ON m2.rowid = messages_fts.rowid
                WHERE messages_fts MATCH :ftsQuery))
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun observeSummaries(query: String, likePattern: String, ftsQuery: String): Flow<List<ChatSummaryRow>>

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE chats SET toolsEnabled = :enabled WHERE id = :id")
    suspend fun setToolsEnabled(id: String, enabled: Boolean)

    @Query("UPDATE chats SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?)

    @Query("UPDATE chats SET currentLeafId = :leafId WHERE id = :id")
    suspend fun setCurrentLeaf(id: String, leafId: String?)

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observe(id: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun get(id: String): ChatEntity?

    @Upsert
    suspend fun upsert(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Restores a deleted chat AND its messages in one database transaction: either both are
     * written or neither is. Without @Transaction, a crash in between could leave a chat with
     * no messages.
     */
    @Transaction
    suspend fun restore(chat: ChatEntity, messages: List<MessageEntity>) {
        upsert(chat)
        insertMessages(messages)
    }

    /** The cached summary of the older part of [chatId], or null if none was written yet. */
    @Query("SELECT * FROM conversation_summaries WHERE chatId = :chatId")
    suspend fun summary(chatId: String): ConversationSummaryEntity?

    @Upsert
    suspend fun upsertSummary(summary: ConversationSummaryEntity)

    /** The visible conversation changed earlier than the newest turn (edit, other version): forget its summary. */
    @Query("DELETE FROM conversation_summaries WHERE chatId = :chatId")
    suspend fun deleteSummary(chatId: String)
}
