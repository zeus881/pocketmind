package com.sanjay.pocketmind.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.data.models.ModelRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the onboarding pages show about this phone. */
data class OnboardingUiState(
    val isLoading: Boolean = true,
    val deviceRamGb: Int = 0,
    val recommendedName: String? = null,
    val recommendedSizeBytes: Long = 0,
)

/**
 * First-launch flow. Shows the recommended model for this phone's RAM and remembers (in
 * DataStore) that onboarding was completed, so it never shows again.
 */
class OnboardingViewModel(private val models: ModelRepository, private val settings: SettingsDataSource) :
    ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _finished = Channel<Unit>(Channel.BUFFERED)

    /** Emits once when onboarding is saved as done; the screen then navigates on. */
    val finished: Flow<Unit> = _finished.receiveAsFlow()

    init {
        viewModelScope.launch {
            val recommendedId = models.recommendedModelId()
            val recommended = models.observeModels().first().firstOrNull { it.info.id == recommendedId }?.info
            _uiState.value = OnboardingUiState(
                isLoading = false,
                deviceRamGb = models.deviceRamGb(),
                recommendedName = recommended?.displayName,
                recommendedSizeBytes = recommended?.sizeBytes ?: 0,
            )
        }
    }

    fun finish() {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            _finished.send(Unit)
        }
    }
}
