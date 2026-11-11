package com.sanjay.pocketmind.data.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reads the text of a PDF chosen with the system file picker. */
interface PdfTextExtractor {
    suspend fun extract(uri: Uri): AppResult<ExtractedDocument>
}

/**
 * [PdfTextExtractor] with PdfBox-Android, page by page, keeping page numbers (needed for
 * citations). Runs on the IO dispatcher: parsing a 30-page PDF takes a few seconds.
 *
 * - The file comes from the Storage Access Framework (`content://` Uri), so the app needs NO
 *   storage permission — the user grants access to exactly one file by picking it.
 * - `MemoryUsageSetting.setupTempFileOnly()` keeps PDFBox's buffers in a temp file instead of
 *   RAM, so a large PDF can't push the app out of memory while the 2.6 GB model is loaded.
 * - Scanned PDFs are images without a text layer → NO_TEXT_IN_DOCUMENT (OCR is a v2 idea).
 */
class PdfBoxTextExtractor(
    private val context: Context,
    private val io: CoroutineDispatcher,
    private val pro: ProStatus,
) : PdfTextExtractor {

    private val initialized = AtomicBoolean(false)

    override suspend fun extract(uri: Uri): AppResult<ExtractedDocument> = withContext(io) {
        // PDFBox needs its font/encoding resources loaded once per process.
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext AppResult.Failure(ErrorKind.FILE_UNREADABLE)
            input.use {
                PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                    // Free version: up to FREE_PDF_PAGES pages (decision 2: Pro reads any length).
                    if (document.numberOfPages > FREE_PDF_PAGES && !pro.isPro.value) {
                        return@withContext AppResult.Failure(ErrorKind.NEEDS_PRO)
                    }
                    val stripper = PDFTextStripper()
                    val pages = (1..document.numberOfPages).map { page ->
                        ensureActive() // leaving the screen cancels a long extraction
                        stripper.startPage = page
                        stripper.endPage = page
                        PageText(page, stripper.getText(document).trim())
                    }
                    val withText = pages.filter { page -> page.text.isNotBlank() }
                    if (withText.isEmpty()) {
                        AppResult.Failure(ErrorKind.NO_TEXT_IN_DOCUMENT)
                    } else {
                        AppResult.Success(ExtractedDocument(displayName(uri), withText))
                    }
                }
            }
        } catch (e: IOException) {
            // Corrupt, password-protected or unreadable file.
            AppResult.Failure(ErrorKind.FILE_UNREADABLE, e)
        }
    }

    companion object {
        /**
         * Pages the free version reads. 50 keeps v1's promise (a 30-page PDF summarized) free with room
         * to spare; the owner may change it. Checked before any text is extracted.
         */
        const val FREE_PDF_PAGES = 50
    }

    private fun displayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val name = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name ?: uri.lastPathSegment.orEmpty()
    }
}
