package com.sanjay.pocketmind.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanjay.pocketmind.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object for messages. Ordered by `createdAt` so a chat reads top to bottom. */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt, id")
    fun observe(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt, id")
    suspend fun getAll(chatId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun count(chatId: String): Int

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    /** Every attached photo still referenced by a message (used to delete photos of deleted chats). */
    @Query("SELECT imagePath FROM messages WHERE imagePath IS NOT NULL")
    suspend fun imagePaths(): List<String>
}
