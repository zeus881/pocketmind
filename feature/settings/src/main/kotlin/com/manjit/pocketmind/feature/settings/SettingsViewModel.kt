package com.manjit.pocketmind.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manjit.pocketmind.core.datastore.SettingsDataSource
import com.manjit.pocketmind.core.llm.DefaultPrompts
import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.llm.LlmEngine
import com.manjit.pocketmind.core.model.ThemeMode
import com.manjit.pocketmind.core.model.UserSettings
import com.manjit.pocketmind.data.models.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val selectedModelName: String? = null,
    /** The hardware the model ACTUALLY loaded on (GPU may fall back to CPU). */
    val activeBackend: String? = null,
) {
    /** What the system prompt editor shows: the custom prompt, or the default. */
    val effectiveSystemPrompt: String get() = settings.customSystemPrompt ?: DefaultPrompts.SYSTEM
}

/**
 * Settings screen. Every change is written to DataStore immediately; the chat reads settings
 * before each message, so changes apply to the next answer (GPU/CPU changes reload the model).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataSource,
    models: ModelRepository,
    engine: LlmEngine,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        models.observeModels(),
        engine.state,
    ) { user, modelList, engineState ->
        SettingsUiState(
            isLoading = false,
            settings = user,
            selectedModelName = modelList.firstOrNull { it.isSelected }?.info?.displayName,
            activeBackend = (engineState as? EngineState.Ready)?.backend?.label,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setPreferGpu(value: Boolean) = save { settings.setPreferGpu(value) }

    fun setTemperature(value: Float) = save { settings.setTemperature(value) }

    fun setTopK(value: Int) = save { settings.setTopK(value) }

    /** Saving the default text (or blank) stores "no custom prompt". */
    fun setSystemPrompt(value: String) = save {
        settings.setCustomSystemPrompt(value.takeUnless { it.trim() == DefaultPrompts.SYSTEM })
    }

    fun resetSystemPrompt() = save { settings.setCustomSystemPrompt(null) }

    fun setThemeMode(mode: ThemeMode) = save { settings.setThemeMode(mode) }

    fun setAllowMobileData(value: Boolean) = save { settings.setAllowMobileData(value) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
