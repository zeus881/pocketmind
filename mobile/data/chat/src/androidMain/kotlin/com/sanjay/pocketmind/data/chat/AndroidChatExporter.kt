package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withSave
import androidx.core.net.toUri
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.model.Message
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Saves a chat as a Markdown or PDF file at a [Uri] the user picked with the system "Save as"
 * screen (Storage Access Framework). The app needs no storage permission: the user grants
 * access to exactly that one file. Nothing leaves the phone unless the user moves the file.
 *
 * The text formats are pure ([ChatMarkdown], [PdfPagination]) and unit-tested; this class only
 * does the writing, on the IO dispatcher.
 */
internal class AndroidChatExporter(private val context: Context, private val io: CoroutineDispatcher) : ChatExporter {
    override suspend fun writeMarkdown(uri: String, title: String, messages: List<Message>): AppResult<Unit> =
        write(uri) { it.write(ChatMarkdown.build(title, messages).toByteArray(Charsets.UTF_8)) }

    /**
     * A4 pages (595 × 842 points, the PDF unit), 48 pt margins, 11 pt text. The whole conversation
     * is laid out once with [StaticLayout] (which wraps lines like a TextView), then cut into pages
     * at line boundaries so no line is ever split across two pages.
     */
    override suspend fun writePdf(uri: String, title: String, messages: List<Message>): AppResult<Unit> =
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

    private suspend fun write(uri: String, block: (java.io.OutputStream) -> Unit): AppResult<Unit> = withContext(io) {
        try {
            // "wt" = write + truncate, so saving over an existing file doesn't leave old bytes at the end.
            val out = context.contentResolver.openOutputStream(uri.toUri(), "wt")
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
