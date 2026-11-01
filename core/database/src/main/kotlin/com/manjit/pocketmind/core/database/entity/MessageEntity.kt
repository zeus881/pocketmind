package com.manjit.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.manjit.pocketmind.core.model.Message
import com.manjit.pocketmind.core.model.MessageStatus
import com.manjit.pocketmind.core.model.TurnRole

/**
 * Table `messages`: every user question and assistant answer.
 *
 * The FOREIGN KEY ties each message to its chat. `onDelete = CASCADE` means: when a chat
 * row is deleted, SQLite itself deletes all of its messages — no orphan rows, and no Kotlin
 * code that could forget to do it.
 *
 * The index on `chatId` is required for performance: "all messages of chat X" is our most
 * frequent query, and without the index SQLite would scan the whole table.
 *
 * Enums (`role`, `status`) are stored as their names (TEXT), which stays readable in a
 * database inspector and survives reordering the enum (storing ordinals would not).
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chatId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val status: String,
    val tokensPerSecond: Double?,
    val backend: String?,
)

fun MessageEntity.asModel() = Message(
    id = id,
    chatId = chatId,
    role = TurnRole.valueOf(role),
    text = text,
    createdAt = createdAt,
    status = MessageStatus.valueOf(status),
    tokensPerSecond = tokensPerSecond,
    backend = backend,
)

fun Message.asEntity() = MessageEntity(
    id = id,
    chatId = chatId,
    role = role.name,
    text = text,
    createdAt = createdAt,
    status = status.name,
    tokensPerSecond = tokensPerSecond,
    backend = backend,
)
