package com.sanjay.pocketmind.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanjay.pocketmind.core.model.ThemeMode
import com.sanjay.pocketmind.core.model.UserSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Reads and writes [UserSettings] with Jetpack DataStore (Preferences).
 *
 * WHY DataStore and not SharedPreferences? DataStore is fully asynchronous (no disk I/O on
 * the main thread), exposes changes as a Flow, and writes are transactional: `edit {}` either
 * applies all changes or none. SharedPreferences can block the UI thread and lose writes.
 *
 * Values are validated (clamped) on write, so a bad value can never reach the engine.
 */
@Singleton
class SettingsDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            selectedModelId = prefs[Keys.SELECTED_MODEL],
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            allowMobileData = prefs[Keys.ALLOW_MOBILE_DATA] ?: false,
            preferGpu = prefs[Keys.PREFER_GPU] ?: true,
            temperature = prefs[Keys.TEMPERATURE] ?: UserSettings.DEFAULT_TEMPERATURE,
            topK = prefs[Keys.TOP_K] ?: UserSettings.DEFAULT_TOP_K,
            customSystemPrompt = prefs[Keys.SYSTEM_PROMPT],
            themeMode = prefs[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            appLockTimeoutSeconds = prefs[Keys.APP_LOCK_TIMEOUT] ?: UserSettings.DEFAULT_LOCK_TIMEOUT_SECONDS,
            keepModelLoadedMinutes = prefs[Keys.KEEP_MODEL_MINUTES] ?: UserSettings.DEFAULT_KEEP_MODEL_MINUTES,
        )
    }

    /** The current value, read once (for code that isn't observing). */
    suspend fun current(): UserSettings = settings.first()

    suspend fun setSelectedModel(id: String?) = update { prefs ->
        if (id == null) prefs.remove(Keys.SELECTED_MODEL) else prefs[Keys.SELECTED_MODEL] = id
    }

    suspend fun setOnboardingDone(done: Boolean) = update { it[Keys.ONBOARDING_DONE] = done }

    suspend fun setAllowMobileData(allow: Boolean) = update { it[Keys.ALLOW_MOBILE_DATA] = allow }

    suspend fun setPreferGpu(prefer: Boolean) = update { it[Keys.PREFER_GPU] = prefer }

    suspend fun setTemperature(value: Float) = update {
        it[Keys.TEMPERATURE] = value.coerceIn(UserSettings.MIN_TEMPERATURE, UserSettings.MAX_TEMPERATURE)
    }

    suspend fun setTopK(value: Int) = update {
        it[Keys.TOP_K] = value.coerceIn(UserSettings.MIN_TOP_K, UserSettings.MAX_TOP_K)
    }

    /** Blank resets to the built-in default prompt. */
    suspend fun setCustomSystemPrompt(prompt: String?) = update { prefs ->
        if (prompt.isNullOrBlank()) prefs.remove(Keys.SYSTEM_PROMPT) else prefs[Keys.SYSTEM_PROMPT] = prompt.trim()
    }

    suspend fun setThemeMode(mode: ThemeMode) = update { it[Keys.THEME] = mode.name }

    suspend fun setAppLockEnabled(enabled: Boolean) = update { it[Keys.APP_LOCK] = enabled }

    /** Only the values offered in Settings are accepted; anything else falls back to the default. */
    suspend fun setAppLockTimeoutSeconds(seconds: Int) = update {
        it[Keys.APP_LOCK_TIMEOUT] = seconds.takeIf { s -> s in UserSettings.LOCK_TIMEOUT_OPTIONS }
            ?: UserSettings.DEFAULT_LOCK_TIMEOUT_SECONDS
    }

    /** Only the values offered in Settings are accepted; anything else falls back to the default. */
    suspend fun setKeepModelLoadedMinutes(minutes: Int) = update {
        it[Keys.KEEP_MODEL_MINUTES] = minutes.takeIf { m -> m in UserSettings.KEEP_MODEL_OPTIONS }
            ?: UserSettings.DEFAULT_KEEP_MODEL_MINUTES
    }

    /**
     * Applies [block] in one DataStore transaction. Returns Unit on purpose: DataStore's own
     * `edit` returns its `Preferences` type, which would leak DataStore into every caller's API.
     */
    private suspend fun update(block: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs -> block(prefs) }
    }

    /** Key names are part of the on-disk format: never rename them. */
    private object Keys {
        val SELECTED_MODEL = stringPreferencesKey("selected_model_id")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data")
        val PREFER_GPU = booleanPreferencesKey("prefer_gpu")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val THEME = stringPreferencesKey("theme_mode")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val APP_LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_s")
        val KEEP_MODEL_MINUTES = intPreferencesKey("keep_model_loaded_min")
    }
}
