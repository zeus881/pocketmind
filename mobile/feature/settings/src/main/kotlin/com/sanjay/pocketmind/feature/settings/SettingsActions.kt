package com.sanjay.pocketmind.feature.settings

import android.app.Activity
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

    /** Writes the ZIP of all the user's data to [uri] (picked with "Save as"). */
    fun exportAllData(uri: String)

    /** Deletes chats, notes, personas, prompts, photos and settings (after the user confirmed). */
    fun deleteAllData()

    /** Opens Play's purchase sheet for PocketMind Pro. */
    fun buyPro(activity: Activity)

    /** Asks Play again which purchases this account owns. */
    fun restorePro()

    /** Builds the diagnostic report and hands it to the share sheet (the user decides where it goes). */
    fun shareDiagnostics()
}
