package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole

/**
 * Saves a chat to a file the user picked. An interface so ViewModel tests can use a fake.
 *
 * [uri] is the location the platform's "Save as" screen returned, as text: a `content://` URI on Android, a
 * file URL on iOS. Text (not android.net.Uri) keeps this interface, and the ViewModels using it, multiplatform.
 */
interface ChatExporter {
    suspend fun writeMarkdown(uri: String, title: String, messages: List<Message>): AppResult<Unit>

    suspend fun writePdf(uri: String, title: String, messages: List<Message>): AppResult<Unit>
}

/** The Markdown version of a chat: a title, then every message under a speaker heading. */
object ChatMarkdown {
    fun build(title: String, messages: List<Message>): String = buildString {
        append("# ").append(title.ifBlank { "PocketMind chat" }).append("\n\n")
        messages.forEach { message ->
            append(if (message.role == TurnRole.USER) "**You:**" else "**PocketMind:**").append("\n\n")
            append(message.text.trim()).append("\n\n")
        }
        append("---\nExported from PocketMind. Answers were generated on this phone and can be wrong.\n")
    }
}

/** Splits laid-out lines into pages. Pure, so the page-break rules are unit-tested. */
object PdfPagination {
    /**
     * [lineBottoms]: the y coordinate where each line ends (increasing). Returns, per page, the
     * range of line indices that fit within [pageHeight]. A single line taller than a page still
     * gets its own page instead of looping forever.
     */
    fun pages(lineBottoms: List<Int>, pageHeight: Int): List<IntRange> {
        val pages = mutableListOf<IntRange>()
        var start = 0
        while (start < lineBottoms.size) {
            val pageTop = if (start == 0) 0 else lineBottoms[start - 1]
            var end = start
            while (end + 1 < lineBottoms.size && lineBottoms[end + 1] - pageTop <= pageHeight) end++
            pages += start..end
            start = end + 1
        }
        return pages
    }
}
