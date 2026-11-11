package com.sanjay.pocketmind.feature.settings

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.billing.BuyResult
import com.sanjay.pocketmind.core.billing.ProStatus
import com.sanjay.pocketmind.core.common.diagnostics.CrashLog
import com.sanjay.pocketmind.core.common.diagnostics.DeviceFactsSource
import com.sanjay.pocketmind.core.common.diagnostics.DiagnosticReport
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.DefaultPrompts
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.model.ThemeMode
import com.sanjay.pocketmind.core.model.UserSettings
import com.sanjay.pocketmind.data.chat.UserDataControl
import com.sanjay.pocketmind.data.models.ModelRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off results shown by the Settings screen. */
sealed interface SettingsEvent {
    data object DataExported : SettingsEvent

    data object ExportFailed : SettingsEvent

    data object DataDeleted : SettingsEvent

    /** No Play Store, not signed in, or the product isn't published yet. */
    data object ProUnavailable : SettingsEvent

    data class Restored(val isPro: Boolean) : SettingsEvent

    /** The report text for the share sheet. */
    data class ShareDiagnostics(val report: String) : SettingsEvent
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val selectedModelName: String? = null,
    /** The hardware the model ACTUALLY loaded on (GPU may fall back to CPU). */
    val activeBackend: String? = null,
    val isPro: Boolean = false,
    /** Exactly as Google Play formats it, in the user's currency; null until Play answers. */
    val proPrice: String? = null,
) {
    /** What the system prompt editor shows: the custom prompt, or the default. */
    val effectiveSystemPrompt: String get() = settings.customSystemPrompt ?: DefaultPrompts.SYSTEM
}

/**
 * Settings screen. Every change is written to DataStore immediately; the chat reads settings
 * before each message, so changes apply to the next answer (GPU/CPU changes reload the model).
 */
class SettingsViewModel(
    private val settings: SettingsDataSource,
    models: ModelRepository,
    private val engine: LlmEngine,
    private val userData: UserDataControl,
    private val pro: ProStatus,
    private val crashLog: CrashLog,
    private val deviceFacts: DeviceFactsSource,
    private val io: CoroutineDispatcher,
) : ViewModel(),
    SettingsActions {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        models.observeModels(),
        engine.state,
        pro.isPro,
        pro.offer,
    ) { user, modelList, engineState, isPro, offer ->
        SettingsUiState(
            isLoading = false,
            settings = user,
            selectedModelName = modelList.firstOrNull { it.isSelected }?.info?.displayName,
            activeBackend = (engineState as? EngineState.Ready)?.backend?.label,
            isPro = isPro,
            proPrice = offer?.formattedPrice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    override fun setPreferGpu(value: Boolean) = save { settings.setPreferGpu(value) }

    override fun setTemperature(value: Float) = save { settings.setTemperature(value) }

    override fun setTopK(value: Int) = save { settings.setTopK(value) }

    /** Saving the default text (or blank) stores "no custom prompt". */
    override fun setSystemPrompt(value: String) = save {
        settings.setCustomSystemPrompt(value.takeUnless { it.trim() == DefaultPrompts.SYSTEM })
    }

    override fun resetSystemPrompt() = save { settings.setCustomSystemPrompt(null) }

    override fun setThemeMode(mode: ThemeMode) = save { settings.setThemeMode(mode) }

    override fun setAllowMobileData(value: Boolean) = save { settings.setAllowMobileData(value) }

    override fun setAppLock(enabled: Boolean) = save { settings.setAppLockEnabled(enabled) }

    override fun setAppLockTimeout(seconds: Int) = save { settings.setAppLockTimeoutSeconds(seconds) }

    override fun setKeepModelLoaded(minutes: Int) = save { settings.setKeepModelLoadedMinutes(minutes) }

    override fun setDynamicColor(enabled: Boolean) = save { settings.setDynamicColor(enabled) }

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)

    /** One-off results of the data actions, shown as a snackbar. */
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    override fun exportAllData(uri: Uri) {
        viewModelScope.launch {
            val result = userData.exportAll(uri)
            _events.send(if (result is AppResult.Success) SettingsEvent.DataExported else SettingsEvent.ExportFailed)
        }
    }

    override fun buyPro(activity: Activity) {
        viewModelScope.launch {
            if (pro.buy(activity) == BuyResult.UNAVAILABLE) _events.send(SettingsEvent.ProUnavailable)
        }
    }

    override fun restorePro() {
        viewModelScope.launch {
            pro.refresh()
            _events.send(SettingsEvent.Restored(pro.isPro.value))
        }
    }

    /** Facts only: versions, phone, model and backend, recorded crashes. Never chat text or settings text. */
    override fun shareDiagnostics() {
        viewModelScope.launch {
            val state = uiState.value
            val app = linkedMapOf(
                "Model" to (state.selectedModelName ?: "none"),
                "Backend" to (state.activeBackend ?: "not loaded"),
                "GPU preferred" to state.settings.preferGpu.toString(),
                "Pro" to state.isPro.toString(),
            )
            val report = withContext(io) { DiagnosticReport.build(deviceFacts.facts(), app, crashLog.entries()) }
            _events.send(SettingsEvent.ShareDiagnostics(report))
        }
    }

    override fun deleteAllData() {
        viewModelScope.launch {
            userData.deleteAll()
            _events.send(SettingsEvent.DataDeleted)
        }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
