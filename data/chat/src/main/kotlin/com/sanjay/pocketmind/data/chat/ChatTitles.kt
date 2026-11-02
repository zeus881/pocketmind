package com.sanjay.pocketmind.data.chat

/**
 * Builds the automatic chat title from the first user message.
 *
 * WHY not ask the LLM for a title? That costs a second inference (seconds of latency and
 * battery) for every new chat. A cleaned-up first line is instant and usually good enough;
 * the user can rename any chat.
 */
object ChatTitles {
    const val MAX_LENGTH = 40
    private const val ELLIPSIS = "…"

    fun fromFirstMessage(text: String): String {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val clean = firstLine.replace(Regex("\\s+"), " ").trimEnd('?', '.', '!', ':', ',', ' ')
        if (clean.length <= MAX_LENGTH) return clean
        // Cut at the last space before the limit so we don't split a word in half.
        val cut = clean.take(MAX_LENGTH).let { head ->
            val space = head.lastIndexOf(' ')
            if (space > MAX_LENGTH / 2) head.substring(0, space) else head
        }
        return cut.trimEnd() + ELLIPSIS
    }
}
