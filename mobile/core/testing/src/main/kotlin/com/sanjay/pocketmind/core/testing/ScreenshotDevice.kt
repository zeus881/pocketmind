package com.sanjay.pocketmind.core.testing

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import com.github.takahirom.roborazzi.uiMode
import com.sanjay.pocketmind.core.designsystem.theme.PocketMindTheme
import com.sanjay.pocketmind.core.model.ThemeMode

/**
 * The window/appearance combinations every screen is checked in (CLAUDE.md §0 quality gates).
 * Sizes are typical devices: a 6.3" phone (411×891 dp) and a 10" tablet in landscape (1280×800 dp).
 */
enum class ScreenshotDevice(val widthDp: Int, val heightDp: Int, val fontScale: Float, val dark: Boolean) {
    PHONE_LIGHT(widthDp = 411, heightDp = 891, fontScale = 1f, dark = false),
    PHONE_DARK(widthDp = 411, heightDp = 891, fontScale = 1f, dark = true),
    TABLET(widthDp = 1280, heightDp = 800, fontScale = 1f, dark = false),
    PHONE_FONT_200(widthDp = 411, heightDp = 891, fontScale = 2f, dark = false),
}

/**
 * Renders [content] inside the app theme once per [ScreenshotDevice] and saves
 * `src/test/screenshots/<name>_<device>.png` of the module under test.
 *
 * How it works: Robolectric draws the Compose UI with Android's real graphics code on the JVM
 * (`@GraphicsMode(NATIVE)` on the test class), and Roborazzi captures the pixels.
 * - `./gradlew recordRoborazziDebug` writes the "golden" images (commit them).
 * - `./gradlew verifyRoborazziDebug` re-renders and FAILS if any pixel area changed (CI).
 * - A plain `testDebugUnitTest` skips the capture (Roborazzi does nothing outside those tasks).
 *
 * Wallpaper colours are off so images are identical on every machine.
 * The size/font/ui-mode options are marked experimental by Roborazzi, hence the opt-in.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureScreenshots(
    name: String,
    devices: List<ScreenshotDevice> = ScreenshotDevice.entries,
    content: @Composable () -> Unit,
) {
    devices.forEach { device ->
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_${device.name.lowercase()}.png",
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = device.widthDp, heightDp = device.heightDp)
                fontScale(device.fontScale)
                if (device.dark) uiMode(Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
            },
        ) {
            PocketMindTheme(themeMode = if (device.dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false) {
                content()
            }
        }
    }
}
