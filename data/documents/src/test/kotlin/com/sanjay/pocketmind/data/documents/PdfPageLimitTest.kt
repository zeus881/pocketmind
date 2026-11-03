package com.sanjay.pocketmind.data.documents

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.testing.FakeProStatus
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The free version's PDF limit, with real PDF files made by PdfBox. */
@RunWith(RobolectricTestRunner::class)
class PdfPageLimitTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A PDF with [pages] empty pages (no text layer). */
    private fun pdf(pages: Int): Uri {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "pages-$pages.pdf")
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(file)
        }
        return Uri.fromFile(file)
    }

    private fun extractor(pro: Boolean) = PdfBoxTextExtractor(context, Dispatchers.Unconfined, FakeProStatus(pro))

    private fun AppResult<*>.kind() = (this as AppResult.Failure).kind

    @Test
    fun `without Pro a PDF over the free limit is refused before any text is read`() = runTest {
        val long = pdf(PdfBoxTextExtractor.FREE_PDF_PAGES + 1)

        assertEquals(ErrorKind.NEEDS_PRO, extractor(pro = false).extract(long).kind())
    }

    @Test
    fun `with Pro, or within the limit, the PDF is read`() = runTest {
        // Empty pages have no text, so reading them ends in NO_TEXT_IN_DOCUMENT: proof that they were read.
        val overLimit = pdf(PdfBoxTextExtractor.FREE_PDF_PAGES + 1)
        val atLimit = pdf(PdfBoxTextExtractor.FREE_PDF_PAGES)
        assertEquals(ErrorKind.NO_TEXT_IN_DOCUMENT, extractor(pro = true).extract(overLimit).kind())
        assertEquals(ErrorKind.NO_TEXT_IN_DOCUMENT, extractor(pro = false).extract(atLimit).kind())
    }

    @Test
    fun `a file that is not a PDF is reported as unreadable`() = runTest {
        val text = File(context.cacheDir, "notes.pdf").apply { writeText("not a pdf") }

        assertEquals(ErrorKind.FILE_UNREADABLE, extractor(pro = true).extract(Uri.fromFile(text)).kind())
    }
}
