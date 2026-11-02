package com.sanjay.pocketmind.feature.settings

import com.sanjay.pocketmind.core.model.ThemeMode

/**
 * Everything the Settings screen can ask for. [SettingsViewModel] implements it; screenshot and
 * UI tests pass a no-op implementation, so the screen can be drawn without Hilt or DataStore.
 */
interface SettingsActions {
    fun setPreferGpu(value: Boolean)
    fun setTemperature(value: Float)
    fun setTopK(value: Int)
    fun setSystemPrompt(value: String)
    fun resetSystemPrompt()
    fun setThemeMode(mode: ThemeMode)
    fun setAllowMobileData(value: Boolean)
    fun setAppLock(enabled: Boolean)
    fun setAppLockTimeout(seconds: Int)
    fun setKeepModelLoaded(minutes: Int)
    fun setDynamicColor(enabled: Boolean)
}
