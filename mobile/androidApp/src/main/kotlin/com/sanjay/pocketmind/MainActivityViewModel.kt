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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * App lock lifecycle:
 * - Cold start with the lock enabled → locked.
 * - Turning the lock ON in Settings doesn't lock you out right there; turning it OFF unlocks.
 * - Leaving the app / coming back: [LockTimer] decides, using the user's timeout and the
 *   picker exemption. Times come from the Activity (`SystemClock.elapsedRealtime()`), which
 *   keeps counting during sleep and can't be changed by the user, unlike the wall clock.
 *
 * The ViewModel survives rotation, so rotating the phone never asks for the fingerprint again.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(settings: SettingsDataSource) : ViewModel() {

    private val latestSettings = MutableStateFlow<UserSettings?>(null)
    private val locked = MutableStateFlow(false)
    private val lockTimer = LockTimer()

    val uiState: StateFlow<MainUiState> = combine(latestSettings.filterNotNull(), locked) { s, isLocked ->
        MainUiState.Ready(s, locked = s.appLockEnabled && isLocked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState.Loading)

    init {
        viewModelScope.launch {
            var wasEnabled: Boolean? = null
            settings.settings.collect { s ->
                // Cold start: locked if enabled. Switched off: unlocked. Just switched on: the
                // user is clearly here, so stay unlocked until they leave the app.
                val justSwitchedOn = wasEnabled == false && s.appLockEnabled
                if (wasEnabled == null) locked.value = s.appLockEnabled
                if (!s.appLockEnabled || justSwitchedOn) locked.value = false
                wasEnabled = s.appLockEnabled
                latestSettings.value = s
            }
        }
    }

    private val _pendingLaunch = MutableStateFlow<LaunchRequest?>(null)

    /** A request from the widget, the tile or a share, waiting for the app frame to navigate. */
    val pendingLaunch: StateFlow<LaunchRequest?> = _pendingLaunch

    fun onLaunchRequest(request: LaunchRequest?) {
        if (request != null) _pendingLaunch.value = request
    }

    /** The app frame navigated for the request: don't do it again (e.g. after rotation). */
    fun onLaunchHandled() {
        _pendingLaunch.value = null
    }

    fun unlock() {
        locked.value = false
    }

    /** We start a picker and expect to come back with a result (doesn't count as leaving). */
    fun onLaunchingForResult() = lockTimer.onLaunchingForResult()

    fun onAppBackground(nowMs: Long) = lockTimer.onBackground(nowMs, isLocked = locked.value)

    fun onAppForeground(nowMs: Long) {
        val s = latestSettings.value ?: return
        if (lockTimer.shouldLockOnForeground(nowMs, s.appLockEnabled, s.appLockTimeoutSeconds * MILLIS_PER_SECOND)) {
            locked.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MILLIS_PER_SECOND = 1000L
    }
}
