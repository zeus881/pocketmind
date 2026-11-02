package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withSave
import com.sanjay.pocketmind.core.common.dispatchers.Dispatcher
import com.sanjay.pocketmind.core.common.dispatchers.PmDispatchers
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Saves a chat to a file the user picked. An interface so ViewModel tests can use a fake. */
interface ChatExporter {
    suspend fun writeMarkdown(uri: Uri, title: String, messages: List<Message>): AppResult<Unit>

    suspend fun writePdf(uri: Uri, title: String, messages: List<Message>): AppResult<Unit>
}

/**
 * Saves a chat as a Markdown or PDF file at a [Uri] the user picked with the system "Save as"
 * screen (Storage Access Framework). The app needs no storage permission: the user grants
 * access to exactly that one file. Nothing leaves the phone unless the user moves the file.
 *
 * The text formats are pure ([ChatMarkdown], [PdfPagination]) and unit-tested; this class only
 * does the writing, on the IO dispatcher.
 */
internal class AndroidChatExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(PmDispatchers.IO) private val io: CoroutineDispatcher,
) : ChatExporter {
    override suspend fun writeMarkdown(uri: Uri, title: String, messages: List<Message>): AppResult<Unit> =
        write(uri) { it.write(ChatMarkdown.build(title, messages).toByteArray(Charsets.UTF_8)) }

    /**
     * A4 pages (595 × 842 points, the PDF unit), 48 pt margins, 11 pt text. The whole conversation
     * is laid out once with [StaticLayout] (which wraps lines like a TextView), then cut into pages
     * at line boundaries so no line is ever split across two pages.
     */
    override suspend fun writePdf(uri: Uri, title: String, messages: List<Message>): AppResult<Unit> =
        write(uri) { out -> drawPdf(out, ChatMarkdown.build(title, messages)) }

    private fun drawPdf(out: java.io.OutputStream, text: String) {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = PDF_TEXT_SIZE }
        val width = PAGE_WIDTH - 2 * MARGIN
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        val lineBottoms = (0 until layout.lineCount).map { layout.getLineBottom(it) }
        val document = PdfDocument()
        try {
            PdfPagination.pages(lineBottoms, PAGE_HEIGHT - 2 * MARGIN).forEachIndexed { index, lines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                val top = layout.getLineTop(lines.first)
                val bottom = layout.getLineBottom(lines.last)
                page.canvas.withSave {
                    translate(MARGIN.toFloat(), (MARGIN - top).toFloat())
                    clipRect(0, top, width, bottom)
                    layout.draw(this)
                }
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private suspend fun write(uri: Uri, block: (java.io.OutputStream) -> Unit): AppResult<Unit> = withContext(io) {
        try {
            // "wt" = write + truncate, so saving over an existing file doesn't leave old bytes at the end.
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext AppResult.Failure(ErrorKind.FILE_UNREADABLE)
            out.use(block)
            AppResult.Success(Unit)
        } catch (e: IOException) {
            AppResult.Failure(ErrorKind.FILE_UNREADABLE, e)
        } catch (e: SecurityException) {
            AppResult.Failure(ErrorKind.FILE_UNREADABLE, e)
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48
        const val PDF_TEXT_SIZE = 11f
    }
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
