package com.sanjay.pocketmind.feature.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.data.documents.PdfTextExtractor
import com.sanjay.pocketmind.data.documents.Summarizer
import com.sanjay.pocketmind.data.documents.SummaryProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Every state of the "Summarize a PDF" screen. */
sealed interface SummarizeUiState {
    /** Nothing picked yet (the empty state). */
    data object Idle : SummarizeUiState

    data object ReadingPdf : SummarizeUiState

    data class Summarizing(val documentName: String, val pageCount: Int, val progress: SummaryProgress) :
        SummarizeUiState

    data class Done(val documentName: String, val pageCount: Int, val summary: String, val parts: Int) :
        SummarizeUiState

    data class Error(val kind: ErrorKind) : SummarizeUiState
}

/**
 * Picks a PDF → extracts its text page by page → map-reduce summary with live progress.
 * The whole pipeline is one coroutine Job, so "Cancel" (or leaving the screen) stops it at once,
 * including the model generation in progress.
 */
class SummarizeViewModel(private val extractor: PdfTextExtractor, private val summarizer: Summarizer) : ViewModel() {

    private val _uiState = MutableStateFlow<SummarizeUiState>(SummarizeUiState.Idle)
    val uiState: StateFlow<SummarizeUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var lastUri: String? = null

    @Suppress("TooGenericExceptionCaught") // malformed PDFs / engine failures must not crash the app
    fun onPdfPicked(uri: String) {
        lastUri = uri
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = SummarizeUiState.ReadingPdf
            try {
                summarize(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // e.g. the model was unloaded for memory mid-summary, or PdfBox hit a broken file.
                Logger.e(e) { "Summarizing failed" }
                _uiState.value = SummarizeUiState.Error(ErrorKind.GENERATION_FAILED)
            }
        }
    }

    private suspend fun summarize(uri: String) {
        when (val extracted = extractor.extract(uri)) {
            is AppResult.Failure -> _uiState.value = SummarizeUiState.Error(extracted.kind)

            is AppResult.Success -> {
                val doc = extracted.value
                summarizer.summarize(doc.pages).collect { progress ->
                    _uiState.value = when (progress) {
                        is SummaryProgress.Done ->
                            SummarizeUiState.Done(doc.name, doc.pageCount, progress.summary, progress.parts)

                        is SummaryProgress.Failed -> SummarizeUiState.Error(progress.kind)

                        else -> SummarizeUiState.Summarizing(doc.name, doc.pageCount, progress)
                    }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _uiState.value = SummarizeUiState.Idle
    }

    fun retry() {
        lastUri?.let(::onPdfPicked)
    }
}
