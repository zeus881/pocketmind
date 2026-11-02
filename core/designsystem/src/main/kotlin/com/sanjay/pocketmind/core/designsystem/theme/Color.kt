package com.sanjay.pocketmind.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PocketMind brand palette: a calm teal ("private, on-device") with a warm accent.
// Used on phones older than Android 12, where Material You dynamic colour is not available.

private val Teal10 = Color(0xFF002020)
private val Teal20 = Color(0xFF003737)
private val Teal30 = Color(0xFF004F4F)
private val Teal40 = Color(0xFF006A6A)
private val Teal80 = Color(0xFF4CDADA)
private val Teal90 = Color(0xFF6FF7F6)

private val Amber10 = Color(0xFF2B1700)
private val Amber20 = Color(0xFF472A00)
private val Amber30 = Color(0xFF653E00)
private val Amber40 = Color(0xFF855400)
private val Amber80 = Color(0xFFFFB95C)
private val Amber90 = Color(0xFFFFDDB7)

private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

private val Grey10 = Color(0xFF191C1C)
private val Grey90 = Color(0xFFE0E3E2)
private val Grey95 = Color(0xFFEFF1F0)
private val Grey99 = Color(0xFFFAFDFC)
private val GreyVariant30 = Color(0xFF3F4948)
private val GreyVariant50 = Color(0xFF6F7978)
private val GreyVariant60 = Color(0xFF889392)
private val GreyVariant80 = Color(0xFFBEC9C8)
private val GreyVariant90 = Color(0xFFDAE5E4)

internal val PocketMindLightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Grey99,
    onBackground = Grey10,
    surface = Grey99,
    onSurface = Grey10,
    surfaceVariant = GreyVariant90,
    onSurfaceVariant = GreyVariant30,
    outline = GreyVariant50,
    inverseSurface = Grey10,
    inverseOnSurface = Grey95,
)

internal val PocketMindDarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Amber80,
    onSecondary = Amber20,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Grey10,
    onBackground = Grey90,
    surface = Grey10,
    onSurface = Grey90,
    surfaceVariant = GreyVariant30,
    onSurfaceVariant = GreyVariant80,
    outline = GreyVariant60,
    inverseSurface = Grey90,
    inverseOnSurface = Grey10,
)
