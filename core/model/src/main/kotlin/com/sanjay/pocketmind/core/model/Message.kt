package com.sanjay.pocketmind.core.model

/**
 * One chat message as the UI and the database see it.
 *
 * For assistant messages we also keep the performance numbers of the answer, so the
 * "12.4 tok/s · GPU" label is still correct when the chat is reopened next week.
 */
data class Message(
    val id: String,
    val chatId: String,
    val role: TurnRole,
    val text: String,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val tokensPerSecond: Double? = null,
    val backend: String? = null,
    /** The message this one answers or follows (null for the first message). See [Chat.currentLeafId]. */
    val parentId: String? = null,
    /** v7: a photo the user attached to this question (a JPEG in the app's private storage), or null. */
    val imagePath: String? = null,
)

/** How an assistant answer ended. User messages are always [COMPLETE]. */
enum class MessageStatus {
    COMPLETE,

    /** The user pressed Stop or left the screen; [Message.text] holds the partial answer. */
    STOPPED,

    /** The engine failed mid-answer; the user can retry. */
    FAILED,
}

/**
 * Where a message sits among its alternatives: version [index] (0-based) of [count]. Editing a
 * question or regenerating an answer creates another version; the chat shows "‹ 2 / 3 ›".
 */
data class BranchInfo(val index: Int, val count: Int)
