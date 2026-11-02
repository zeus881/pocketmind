package com.sanjay.pocketmind.core.datastore

import com.sanjay.pocketmind.core.model.ThemeMode
import com.sanjay.pocketmind.core.model.UserSettings
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Uses a REAL DataStore writing to a temporary file — DataStore runs fine on the plain JVM.
 * Each setter suspends until its write is on disk, so reading right after is safe.
 */
class SettingsDataSourceTest {
    private val dataSource by lazy {
        SettingsDataSource(InMemoryPreferencesDataStore())
    }

    @Test
    fun defaultsWhenNothingSaved() = runTest {
        assertEquals(UserSettings(), dataSource.current())
    }

    @Test
    fun valuesRoundTrip() = runTest {
        dataSource.setSelectedModel("gemma-4-e2b")
        dataSource.setOnboardingDone(true)
        dataSource.setThemeMode(ThemeMode.DARK)
        dataSource.setCustomSystemPrompt("  Be brief.  ")

        val s = dataSource.current()
        assertEquals("gemma-4-e2b", s.selectedModelId)
        assertEquals(true, s.onboardingDone)
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals("Be brief.", s.customSystemPrompt)
    }

    @Test
    fun outOfRangeValuesAreClamped() = runTest {
        dataSource.setTemperature(9f)
        dataSource.setTopK(0)
        assertEquals(UserSettings.MAX_TEMPERATURE, dataSource.current().temperature)
        assertEquals(UserSettings.MIN_TOP_K, dataSource.current().topK)
    }

    @Test
    fun blankPromptResetsToDefault() = runTest {
        dataSource.setCustomSystemPrompt("Custom")
        dataSource.setCustomSystemPrompt("   ")
        assertNull(dataSource.current().customSystemPrompt)
    }

    @Test
    fun lockTimeoutAcceptsOnlyOfferedValues() = runTest {
        assertEquals(UserSettings.DEFAULT_LOCK_TIMEOUT_SECONDS, dataSource.current().appLockTimeoutSeconds)
        dataSource.setAppLockTimeoutSeconds(300)
        assertEquals(300, dataSource.current().appLockTimeoutSeconds)
        dataSource.setAppLockTimeoutSeconds(7) // not an option: falls back to the default
        assertEquals(UserSettings.DEFAULT_LOCK_TIMEOUT_SECONDS, dataSource.current().appLockTimeoutSeconds)
    }

    @Test
    fun keepModelLoadedAcceptsOnlyOfferedValues() = runTest {
        assertEquals(UserSettings.DEFAULT_KEEP_MODEL_MINUTES, dataSource.current().keepModelLoadedMinutes)
        dataSource.setKeepModelLoadedMinutes(15)
        assertEquals(15, dataSource.current().keepModelLoadedMinutes)
        dataSource.setKeepModelLoadedMinutes(42)
        assertEquals(UserSettings.DEFAULT_KEEP_MODEL_MINUTES, dataSource.current().keepModelLoadedMinutes)
    }
}
