package com.sanjay.pocketmind.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sanjay.pocketmind.core.model.ThemeMode

/**
 * The single Material 3 theme for the whole app.
 *
 * - [themeMode] comes from the user's setting (system / light / dark).
 * - On Android 12+ "dynamic colour" (colours taken from the user's wallpaper) is used when
 *   [dynamicColor] is on (the user's setting, on by default). Otherwise, and on older
 *   phones, the PocketMind brand palette.
 * - Type scale, shapes and the other tokens (Tokens.kt) are the same in every case.
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
        typography = PocketMindTypography,
        shapes = PocketMindShapes,
        content = content,
    )
}
