package com.sanjay.pocketmind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** App-level state: are settings loaded yet, and what are they (theme, onboarding, lock)? */
sealed interface MainUiState {
    /** Settings are being read from disk (a few milliseconds on first frame). */
    data object Loading : MainUiState

    /** [locked] = the optional app lock is on and the user hasn't unlocked yet. */
    data class Ready(val settings: UserSettings, val locked: Boolean) : MainUiState
}

/**
 * Decides the start screen (onboarding or chat), the theme and whether the app lock shows.
 * We wait for the settings instead of guessing, so users never see onboarding flash up.
 *
 * App lock: when enabled, the app locks every time it goes to the background ([lock]) and
 * unlocks after a successful BiometricPrompt ([unlock]). The ViewModel survives rotation, so
 * rotating the phone doesn't ask for the fingerprint again.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(settings: SettingsDataSource) : ViewModel() {

    private val unlocked = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(settings.settings, unlocked) { s, isUnlocked ->
        MainUiState.Ready(s, locked = s.appLockEnabled && !isUnlocked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState.Loading)

    fun unlock() {
        unlocked.value = true
    }

    fun lock() {
        unlocked.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
