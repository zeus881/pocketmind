package com.sanjay.pocketmind.feature.documents

import com.sanjay.pocketmind.core.testing.captureScreenshots
import com.sanjay.pocketmind.data.documents.SummaryProgress
import com.sanjay.pocketmind.data.documents.rag.IndexedDocument
import com.sanjay.pocketmind.data.documents.rag.SourceChunk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of the PDF summary and Ask-your-PDF panes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentsScreenshotTest {
    @Test
    fun summarizeEmpty() = captureScreenshots("documents_summarize_empty") {
        SummarizePane(SummarizeUiState.Idle, onPickPdf = {}, onCancel = {}, onRetry = {})
    }

    @Test
    fun summarizing() = captureScreenshots("documents_summarizing") {
        SummarizePane(
            SummarizeUiState.Summarizing("Drone manual.pdf", 30, SummaryProgress.Mapping(part = 3, total = 8)),
            onPickPdf = {},
            onCancel = {},
            onRetry = {},
        )
    }

    @Test
    fun summaryDone() = captureScreenshots("documents_summary") {
        SummarizePane(
            SummarizeUiState.Done(
                "Drone manual.pdf",
                30,
                "**Key points**\n\n- Calibrate the compass before the first flight.\n- Keep the battery above 20 %.",
                parts = 8,
            ),
            onPickPdf = {},
            onCancel = {},
            onRetry = {},
        )
    }

    @Test
    fun askWithAnswer() = captureScreenshots("documents_ask") {
        AskPane(
            state = AskUiState(
                isLoading = false,
                documents = listOf(IndexedDocument("d1", "Drone manual.pdf", 30, 42)),
                selectedId = "d1",
                question = "How do I calibrate the compass?",
                answer = "Rotate the drone slowly around each axis until the light turns green [p. 12].",
                sources = listOf(SourceChunk(5, 12, 12, "Compass calibration: rotate…", 0.82f)),
                citedPages = setOf(12),
            ),
            actions = AskActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
