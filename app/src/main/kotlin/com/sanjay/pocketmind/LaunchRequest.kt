package com.sanjay.pocketmind

/**
 * Why the app was opened from outside: the home-screen widget, the quick-settings tile, or
 * "Share → PocketMind" in another app. [MainActivity] turns the Intent into one of these, and
 * the app frame turns it into navigation, so the rest of the app never sees Intents.
 */
sealed interface LaunchRequest {
    /** Widget or tile: start a fresh chat. */
    data object NewChat : LaunchRequest

    /** Widget: open a recent chat. */
    data class OpenChat(val chatId: String) : LaunchRequest

    /** Shared text: a new chat with the text ready to send (the user decides). */
    data class ShareText(val text: String) : LaunchRequest

    /** A shared PDF: summarize it. [uri] is a content:// URI the sharing app granted us. */
    data class SharePdf(val uri: String) : LaunchRequest
}

object LaunchRequests {
    const val ACTION_NEW_CHAT = "com.sanjay.pocketmind.action.NEW_CHAT"
    const val ACTION_OPEN_CHAT = "com.sanjay.pocketmind.action.OPEN_CHAT"
    const val EXTRA_CHAT_ID = "com.sanjay.pocketmind.extra.CHAT_ID"

    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val MIME_PDF = "application/pdf"

    /** Shared text is cut here: a prompt of this size already fills most of the context window. */
    const val MAX_SHARED_TEXT = 8_000

    /**
     * Pure parsing of the Intent's parts (so it is unit-tested without Android):
     * - [subject] + [text] of shared text are joined ("Title\n\nbody"), trimmed and capped;
     * - anything unknown or empty gives null (the app just opens normally).
     */
    fun parse(
        action: String?,
        mimeType: String?,
        text: String? = null,
        subject: String? = null,
        streamUri: String? = null,
        chatId: String? = null,
    ): LaunchRequest? = when (action) {
        ACTION_NEW_CHAT -> LaunchRequest.NewChat

        ACTION_OPEN_CHAT -> chatId?.takeIf { it.isNotBlank() }?.let { LaunchRequest.OpenChat(it) }

        ACTION_SEND -> when {
            mimeType == MIME_PDF && !streamUri.isNullOrBlank() -> LaunchRequest.SharePdf(streamUri)

            mimeType?.startsWith("text/") == true -> listOfNotNull(subject, text)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n\n")
                .take(MAX_SHARED_TEXT)
                .takeIf { it.isNotEmpty() }
                ?.let { LaunchRequest.ShareText(it) }

            else -> null
        }

        else -> null
    }
}
