package com.manjit.pocketmind.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.manjit.pocketmind.core.model.ThemeMode

/**
 * The single Material 3 theme for the whole app.
 *
 * - [themeMode] comes from the user's setting (system / light / dark).
 * - On Android 12+ we use "dynamic colour" (colours taken from the user's wallpaper),
 *   which makes the app feel native. Older phones get our brand palette.
 *
 * Every screen is wrapped in this once (in MainActivity), so all composables read colours
 * and text styles from `MaterialTheme` instead of hard-coding them. That is what makes
 * dark mode work everywhere automatically.
 */
@Composable
fun PocketMindTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> PocketMindDarkColors

        else -> PocketMindLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        // Material 3 default type scale. Sizes are in "sp", so they grow with the user's
        // font-size setting (important for accessibility / large fonts).
        typography = Typography(),
        content = content,
    )
}
