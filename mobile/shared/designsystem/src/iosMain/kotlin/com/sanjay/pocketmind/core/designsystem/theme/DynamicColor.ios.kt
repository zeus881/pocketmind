package com.sanjay.pocketmind.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS has no wallpaper-based app colours: PocketMind's own palette is always used. */
@Composable
internal actual fun dynamicColorScheme(darkTheme: Boolean): ColorScheme? = null

/** iOS has no wallpaper-based app colours: PocketMind always uses its brand colours there. */
actual val dynamicColorAvailable: Boolean = false
