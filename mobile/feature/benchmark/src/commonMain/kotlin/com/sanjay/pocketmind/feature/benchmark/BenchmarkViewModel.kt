package com.sanjay.pocketmind.feature.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.model.BenchmarkResult
import com.sanjay.pocketmind.data.benchmark.BenchmarkProgress
import com.sanjay.pocketmind.data.benchmark.BenchmarkReport
import com.sanjay.pocketmind.data.benchmark.BenchmarkRepository
import com.sanjay.pocketmind.data.benchmark.BenchmarkRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val isLoadingHistory: Boolean = true,
    /** Null when no run is in progress. */
    val progress: BenchmarkProgress? = null,
    val runs: List<BenchmarkResult> = emptyList(),
) {
    val isRunning: Boolean get() = progress != null
}

sealed interface BenchmarkEvent {
    data class Error(val kind: ErrorKind) : BenchmarkEvent

    /** Opens the platform share sheet (Android chooser, iOS activity sheet) with this text. */
    data class Share(val text: String) : BenchmarkEvent
}

class BenchmarkViewModel(private val runner: BenchmarkRunner, private val repository: BenchmarkRepository) :
    ViewModel() {

    private val progress = MutableStateFlow<BenchmarkProgress?>(null)
    private var job: Job? = null
    private val _events = Channel<BenchmarkEvent>(Channel.BUFFERED)
    val events: Flow<BenchmarkEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<BenchmarkUiState> = combine(repository.observeRuns(), progress) { runs, current ->
        BenchmarkUiState(isLoadingHistory = false, progress = current, runs = runs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BenchmarkUiState())

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            runner.run()
                .catch { _events.send(BenchmarkEvent.Error(ErrorKind.GENERATION_FAILED)) }
                .collect { step ->
                    when (step) {
                        is BenchmarkProgress.Finished -> progress.value = null

                        // result arrives via Room
                        is BenchmarkProgress.Failed -> {
                            progress.value = null
                            _events.send(BenchmarkEvent.Error(step.kind))
                        }

                        else -> progress.value = step
                    }
                }
            progress.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        progress.value = null
    }

    fun share(result: BenchmarkResult) {
        _events.trySend(BenchmarkEvent.Share(BenchmarkReport.format(result)))
    }

    fun delete(result: BenchmarkResult) {
        viewModelScope.launch { repository.delete(result.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
