package com.sanjay.pocketmind.feature.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.common.flow.throttleLatest
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.data.documents.rag.Citations
import com.sanjay.pocketmind.data.documents.rag.DocumentRepository
import com.sanjay.pocketmind.data.documents.rag.IndexProgress
import com.sanjay.pocketmind.data.documents.rag.IndexedDocument
import com.sanjay.pocketmind.data.documents.rag.SourceChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AskUiState(
    val isLoading: Boolean = true,
    val embeddingModelReady: Boolean = true,
    val documents: List<IndexedDocument> = emptyList(),
    val selectedId: String? = null,
    /** Non-null while a PDF is being indexed. */
    val indexing: IndexProgress? = null,
    val question: String = "",
    val answer: String? = null,
    val sources: List<SourceChunk> = emptyList(),
    /** Pages the answer actually cites — their chips are highlighted. */
    val citedPages: Set<Int> = emptySet(),
    val isAnswering: Boolean = false,
    val error: ErrorKind? = null,
) {
    val canAsk: Boolean get() = selectedId != null && question.isNotBlank() && !isAnswering && indexing == null
}

/** "Ask your PDF": index PDFs, pick one, ask questions, get answers with page citations. */
class AskViewModel(private val repository: DocumentRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = _uiState.asStateFlow()

    private var answerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeDocuments().collect { docs ->
                _uiState.update { s ->
                    // Keep the selection if it still exists, otherwise pick the newest document.
                    val kept = s.selectedId?.takeIf { id -> docs.any { it.id == id } }
                    s.copy(isLoading = false, documents = docs, selectedId = kept ?: docs.firstOrNull()?.id)
                }
            }
        }
        refreshModelState()
    }

    /** Called when the screen becomes visible: the user may have just downloaded the model. */
    fun refreshModelState() {
        viewModelScope.launch { _uiState.update { it.copy(embeddingModelReady = repository.isEmbeddingModelReady()) } }
    }

    fun addPdf(uri: Uri) {
        viewModelScope.launch {
            repository.index(uri)
                // A native embedding error or a broken PDF becomes a message, never a crash.
                .catch { e ->
                    Timber.e(e, "Indexing failed")
                    emit(IndexProgress.Failed(ErrorKind.FILE_UNREADABLE))
                }
                .collect { progress ->
                    _uiState.update { s ->
                        when (progress) {
                            is IndexProgress.Done ->
                                s.copy(indexing = null, selectedId = progress.document.id, error = null)

                            is IndexProgress.Failed -> s.copy(indexing = null, error = progress.kind)

                            else -> s.copy(indexing = progress, error = null)
                        }
                    }
                }
        }
    }

    fun select(id: String) = _uiState.update { it.copy(selectedId = id, answer = null, sources = emptyList()) }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun onQuestionChange(text: String) = _uiState.update { it.copy(question = text) }

    @Suppress("TooGenericExceptionCaught") // engine failures become a readable error
    fun ask() {
        val s = _uiState.value
        val documentId = s.selectedId ?: return
        if (!s.canAsk) return
        answerJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnswering = true, answer = "", sources = emptyList(), error = null) }
            try {
                when (val result = repository.ask(documentId, s.question.trim())) {
                    is AppResult.Failure -> _uiState.update { it.copy(error = result.kind, answer = null) }

                    is AppResult.Success -> {
                        _uiState.update { it.copy(sources = result.value.sources) }
                        result.value.text
                            .runningFold("") { acc, piece -> acc + piece }
                            .throttleLatest(UI_THROTTLE_MS)
                            .collect { text ->
                                val cited = Citations.pages(text).toSet()
                                _uiState.update { it.copy(answer = text, citedPages = cited) }
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Answering failed")
                _uiState.update { it.copy(error = ErrorKind.GENERATION_FAILED) }
            } finally {
                _uiState.update { it.copy(isAnswering = false) }
            }
        }
    }

    fun stop() {
        answerJob?.cancel()
    }

    private companion object {
        const val UI_THROTTLE_MS = 50L
    }
}
