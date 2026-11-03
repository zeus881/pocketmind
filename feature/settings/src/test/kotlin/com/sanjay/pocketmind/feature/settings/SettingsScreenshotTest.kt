package com.sanjay.pocketmind.feature.settings

import com.sanjay.pocketmind.core.model.ThemeMode
import com.sanjay.pocketmind.core.model.UserSettings
import com.sanjay.pocketmind.core.testing.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of Settings (record: `./gradlew :feature:settings:recordRoborazziDebug`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    private val noActions = object : SettingsActions {
        override fun setPreferGpu(value: Boolean) = Unit
        override fun setTemperature(value: Float) = Unit
        override fun setTopK(value: Int) = Unit
        override fun setSystemPrompt(value: String) = Unit
        override fun resetSystemPrompt() = Unit
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setAllowMobileData(value: Boolean) = Unit
        override fun setAppLock(enabled: Boolean) = Unit
        override fun setAppLockTimeout(seconds: Int) = Unit
        override fun setKeepModelLoaded(minutes: Int) = Unit
        override fun setDynamicColor(enabled: Boolean) = Unit

        override fun exportAllData(uri: android.net.Uri) = Unit

        override fun deleteAllData() = Unit
    }

    @Test
    fun settings() = captureScreenshots("settings") {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                settings = UserSettings(appLockEnabled = true),
                selectedModelName = "Gemma 4 E2B",
                activeBackend = "GPU",
            ),
            vm = noActions,
            navigation = SettingsNavigation(onOpenModels = {}, onOpenBenchmark = {}, onOpenLicenses = {}),
        )
    }
}
