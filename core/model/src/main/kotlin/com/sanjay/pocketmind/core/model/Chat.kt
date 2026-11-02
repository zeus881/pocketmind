package com.sanjay.pocketmind.core.model

/** A saved conversation. [title] is auto-generated from the first message and can be renamed. */
data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Pinned chats stay at the top of History. */
    val pinned: Boolean = false,
    /** The folder the chat is filed in, or null. */
    val folderId: String? = null,
    /**
     * The newest message of the branch currently shown. A chat is a TREE of messages (editing a
     * question or regenerating an answer adds a sibling); the visible conversation is the path from
     * this message back to the first one. Null for an empty chat.
     */
    val currentLeafId: String? = null,
)

/** A named group of chats in History. */
data class Folder(val id: String, val name: String, val createdAt: Long)

/** One row in the history list: the chat plus a preview of its newest message. */
data class ChatSummary(val chat: Chat, val lastMessage: String?)

/**
 * Everything needed to undo a delete: the chat and all its messages.
 * The history screen keeps this in memory for a few seconds while "Undo" is visible.
 */
data class DeletedChat(val chat: Chat, val messages: List<Message>)
