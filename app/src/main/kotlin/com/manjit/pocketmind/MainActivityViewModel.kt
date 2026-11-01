package com.manjit.pocketmind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manjit.pocketmind.core.datastore.SettingsDataSource
import com.manjit.pocketmind.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-level state: are settings loaded yet, and what are they (theme, onboarding)? */
sealed interface MainUiState {
    /** Settings are being read from disk (a few milliseconds on first frame). */
    data object Loading : MainUiState

    data class Ready(val settings: UserSettings) : MainUiState
}

/**
 * Decides the start screen (onboarding or chat) and the theme. We wait for the settings
 * instead of guessing, so users never see onboarding flash up and disappear.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(settings: SettingsDataSource) : ViewModel() {
    val uiState: StateFlow<MainUiState> = settings.settings
        .map<UserSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
