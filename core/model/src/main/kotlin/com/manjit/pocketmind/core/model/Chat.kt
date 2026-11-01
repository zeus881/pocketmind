package com.manjit.pocketmind.core.model

/** A saved conversation. [title] is auto-generated from the first message and can be renamed. */
data class Chat(val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

/** One row in the history list: the chat plus a preview of its newest message. */
data class ChatSummary(val chat: Chat, val lastMessage: String?)

/**
 * Everything needed to undo a delete: the chat and all its messages.
 * The history screen keeps this in memory for a few seconds while "Undo" is visible.
 */
data class DeletedChat(val chat: Chat, val messages: List<Message>)
