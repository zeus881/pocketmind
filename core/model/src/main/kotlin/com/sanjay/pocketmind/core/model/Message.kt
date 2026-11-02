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
)

/** How an assistant answer ended. User messages are always [COMPLETE]. */
enum class MessageStatus {
    COMPLETE,

    /** The user pressed Stop or left the screen; [Message.text] holds the partial answer. */
    STOPPED,

    /** The engine failed mid-answer; the user can retry. */
    FAILED,
}
