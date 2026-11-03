package com.sanjay.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole

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
    indices = [Index("chatId"), Index("parentId")],
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
    /** v5: the previous message on this branch (null for the first). Edits and regenerations add siblings. */
    val parentId: String? = null,
    /** v7: path of the photo attached to a user question (app-private storage), or null. */
    val imagePath: String? = null,
    /** v8: names of the tools the answer used, comma-separated ("calculator,unit_converter"), or null. */
    val toolsUsed: String? = null,
)

/**
 * Full-text search index over message text (SQLite FTS4), kept in sync with `messages` by
 * triggers that Room creates. `content = messages` means the index stores no second copy of the
 * text, only the search structure. Matching uses whole words and prefixes ("drone*"), and is
 * far faster than `LIKE '%…%'`, which must scan every message (AUDIT M-8).
 */
// unicode61: Unicode-aware word splitting and case folding, so Hindi and accented words are found too.
@Fts4(contentEntity = MessageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(val text: String)

fun MessageEntity.asModel() = Message(
    id = id,
    chatId = chatId,
    role = TurnRole.valueOf(role),
    text = text,
    createdAt = createdAt,
    status = MessageStatus.valueOf(status),
    tokensPerSecond = tokensPerSecond,
    backend = backend,
    parentId = parentId,
    imagePath = imagePath,
    toolsUsed = toolsUsed?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
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
    parentId = parentId,
    imagePath = imagePath,
    toolsUsed = toolsUsed.takeIf { it.isNotEmpty() }?.joinToString(","),
)
