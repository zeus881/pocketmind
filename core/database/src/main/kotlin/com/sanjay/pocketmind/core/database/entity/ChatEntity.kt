package com.sanjay.pocketmind.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanjay.pocketmind.core.model.Chat
import com.sanjay.pocketmind.core.model.Folder

/**
 * Table `chats`: one row per conversation.
 *
 * The id is a random UUID string (not an auto-increment number) so a chat can be created in
 * memory and given an id BEFORE it is saved — and so a deleted chat can be restored with the
 * exact same id on "Undo".
 *
 * The index on `updatedAt` makes "newest chats first" fast even with thousands of chats.
 *
 * v5: [pinned], [folderId] and [currentLeafId] (see [Chat]). `folderId` deliberately has NO SQL
 * foreign key: adding one to an existing table means rebuilding it, and rebuilding `chats`
 * inside a migration risks cascading into `messages`. Deleting a folder clears the column in
 * one UPDATE instead ([FolderDao.deleteFolder]).
 */
@Entity(tableName = "chats", indices = [Index("updatedAt"), Index("folderId")])
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    val folderId: String? = null,
    val currentLeafId: String? = null,
    /** v6: the persona the chat uses (built-in id like "builtin:coder", or a custom persona's id). */
    val personaId: String? = null,
)

fun ChatEntity.asModel() = Chat(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
    folderId = folderId,
    currentLeafId = currentLeafId,
    personaId = personaId,
)

fun Chat.asEntity() = ChatEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
    folderId = folderId,
    currentLeafId = currentLeafId,
    personaId = personaId,
)

/** Table `folders`: named groups of chats. */
@Entity(tableName = "folders")
data class FolderEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long)

fun FolderEntity.asModel() = Folder(id = id, name = name, createdAt = createdAt)
