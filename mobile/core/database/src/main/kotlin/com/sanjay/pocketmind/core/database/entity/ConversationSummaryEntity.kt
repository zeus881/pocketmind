package com.sanjay.pocketmind.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Table `conversation_summaries`: a model-written summary of the OLDER part of a long chat, the
 * part that no longer fits in the model's context window.
 *
 * Writing a summary costs a few seconds of generation, so it is cached: [coveredTurns] says how
 * many of the chat's first turns (user + assistant messages, oldest first) the summary already
 * includes. When more turns fall out of the window, only the new ones are summarized, on top of
 * the old summary. One row per chat; deleting the chat deletes it (CASCADE).
 */
@Entity(
    tableName = "conversation_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ConversationSummaryEntity(
    @PrimaryKey val chatId: String,
    val summary: String,
    val coveredTurns: Int,
    val updatedAt: Long,
)
