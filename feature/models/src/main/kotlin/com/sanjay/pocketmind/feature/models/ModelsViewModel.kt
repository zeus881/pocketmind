package com.sanjay.pocketmind.feature.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.data.models.ModelRepository
import com.sanjay.pocketmind.data.models.ModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val chatModels: List<ModelState> = emptyList(),
    val embeddingModels: List<ModelState> = emptyList(),
    val deviceRamGb: Int = 0,
    val recommendedId: String? = null,
    val allowMobileData: Boolean = false,
    /** Models whose file is being re-checked right now (shows "Checking…" on the card). */
    val verifyingIds: Set<String> = emptySet(),
)

sealed interface ModelsEvent {
    /** [neededBytes] is set for NOT_ENOUGH_STORAGE so the UI can say how much space is needed. */
    data class Error(val kind: ErrorKind, val neededBytes: Long = 0) : ModelsEvent

    /** "Check file" finished and the file is intact. */
    data class Verified(val displayName: String) : ModelsEvent
}

/** Model manager screen: catalog + live download state + RAM recommendation. */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    private val _events = Channel<ModelsEvent>(Channel.BUFFERED)
    val events: Flow<ModelsEvent> = _events.receiveAsFlow()

    private val verifying = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ModelsUiState> = combine(
        repository.observeModels(),
        settings.settings,
        verifying,
    ) { models, user, checking ->
        ModelsUiState(
            isLoading = false,
            chatModels = models.filter { it.info.kind == ModelKind.CHAT },
            embeddingModels = models.filter { it.info.kind == ModelKind.EMBEDDING },
            deviceRamGb = repository.deviceRamGb(),
            recommendedId = repository.recommendedModelId(),
            allowMobileData = user.allowMobileData,
            verifyingIds = checking,
        )
    }
        .catch { emit(ModelsUiState(isLoading = false, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ModelsUiState())

    fun download(modelId: String) {
        viewModelScope.launch {
            val result = repository.download(modelId)
            if (result is AppResult.Failure) {
                _events.send(ModelsEvent.Error(result.kind, repository.requiredFreeBytes(modelId)))
            }
        }
    }

    fun pause(modelId: String) = repository.pause(modelId)

    fun cancel(modelId: String) {
        viewModelScope.launch { repository.cancel(modelId) }
    }

    fun delete(modelId: String) {
        viewModelScope.launch { repository.delete(modelId) }
    }

    /** Hashes the whole file (seconds for gigabytes); a damaged file is deleted by the repository. */
    fun verify(modelId: String) {
        if (modelId in verifying.value) return
        viewModelScope.launch {
            verifying.update { it + modelId }
            try {
                when (val result = repository.verify(modelId)) {
                    is AppResult.Success -> {
                        val name = uiState.value.let { it.chatModels + it.embeddingModels }
                            .firstOrNull { it.info.id == modelId }?.info?.displayName ?: modelId
                        _events.send(ModelsEvent.Verified(name))
                    }

                    is AppResult.Failure -> _events.send(ModelsEvent.Error(result.kind))
                }
            } finally {
                verifying.update { it - modelId }
            }
        }
    }

    fun select(modelId: String) {
        viewModelScope.launch { repository.select(modelId) }
    }

    fun setAllowMobileData(allow: Boolean) {
        viewModelScope.launch { settings.setAllowMobileData(allow) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
