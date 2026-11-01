package com.manjit.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.manjit.pocketmind.core.database.entity.ChatEntity
import com.manjit.pocketmind.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** A history-list row: chat columns plus the newest message text (computed by SQL). */
data class ChatSummaryRow(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
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
     * Chats whose title OR any message contains [query] (empty query = all chats), newest first.
     * `||` is SQL string concatenation; LIKE is case-insensitive for ASCII in SQLite.
     */
    @Query(
        """
        SELECT c.id, c.title, c.createdAt, c.updatedAt,
               (SELECT m.text FROM messages m WHERE m.chatId = c.id
                ORDER BY m.createdAt DESC LIMIT 1) AS lastMessage
        FROM chats c
        WHERE :query = ''
           OR c.title LIKE '%' || :query || '%'
           OR EXISTS (SELECT 1 FROM messages m2 WHERE m2.chatId = c.id AND m2.text LIKE '%' || :query || '%')
        ORDER BY c.updatedAt DESC
        """,
    )
    fun observeSummaries(query: String): Flow<List<ChatSummaryRow>>

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
}
