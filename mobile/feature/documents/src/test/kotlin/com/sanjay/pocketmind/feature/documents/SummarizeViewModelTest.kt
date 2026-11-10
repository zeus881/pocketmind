package com.sanjay.pocketmind.feature.documents

import android.net.Uri
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.documents.ExtractedDocument
import com.sanjay.pocketmind.data.documents.PageText
import com.sanjay.pocketmind.data.documents.PdfTextExtractor
import com.sanjay.pocketmind.data.documents.Summarizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only because `android.net.Uri` needs a real implementation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SummarizeViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private var extraction: AppResult<ExtractedDocument> =
        AppResult.Success(ExtractedDocument("report.pdf", listOf(PageText(1, "Drones fly."))))
    private val engine = FakeLlmEngine(responsePieces = listOf("- Drones fly."))

    private val viewModel by lazy {
        val extractor = object : PdfTextExtractor {
            override suspend fun extract(uri: Uri) = extraction
        }
        val task = LlmTask(
            engine,
            object : InferenceSettingsSource {
                override suspend fun current() = InferenceSettings("/m", true, "S", SamplingParams())
            },
        )
        SummarizeViewModel(extractor, Summarizer(task))
    }

    private val uri = Uri.parse("content://test/report.pdf")

    @Test
    fun pickingAPdfEndsWithTheSummary() = runTest {
        viewModel.onPdfPicked(uri)
        advanceUntilIdle()

        val done = viewModel.uiState.value as SummarizeUiState.Done
        assertEquals("report.pdf", done.documentName)
        assertEquals("- Drones fly.", done.summary)
    }

    @Test
    fun scannedPdfShowsNoTextError() = runTest {
        extraction = AppResult.Failure(ErrorKind.NO_TEXT_IN_DOCUMENT)
        viewModel.onPdfPicked(uri)
        advanceUntilIdle()
        assertEquals(SummarizeUiState.Error(ErrorKind.NO_TEXT_IN_DOCUMENT), viewModel.uiState.value)
    }

    @Test
    fun cancelGoesBackToIdle() = runTest {
        engine.pieceDelayMs = 1_000
        viewModel.onPdfPicked(uri)
        viewModel.cancel()
        advanceUntilIdle()
        assertEquals(SummarizeUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun retryRunsTheLastPdfAgain() = runTest {
        extraction = AppResult.Failure(ErrorKind.FILE_UNREADABLE)
        viewModel.onPdfPicked(uri)
        advanceUntilIdle()
        extraction = AppResult.Success(ExtractedDocument("report.pdf", listOf(PageText(1, "Ok."))))
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SummarizeUiState.Done)
    }
}
