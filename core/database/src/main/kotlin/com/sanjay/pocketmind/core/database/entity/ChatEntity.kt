package com.sanjay.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanjay.pocketmind.core.model.Chat

/**
 * Table `chats`: one row per conversation.
 *
 * The id is a random UUID string (not an auto-increment number) so a chat can be created in
 * memory and given an id BEFORE it is saved — and so a deleted chat can be restored with the
 * exact same id on "Undo".
 *
 * The index on `updatedAt` makes "newest chats first" fast even with thousands of chats.
 */
@Entity(tableName = "chats", indices = [Index("updatedAt")])
data class ChatEntity(@PrimaryKey val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

fun ChatEntity.asModel() = Chat(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)

fun Chat.asEntity() = ChatEntity(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)
