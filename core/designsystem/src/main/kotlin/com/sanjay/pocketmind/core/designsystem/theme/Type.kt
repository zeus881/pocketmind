package com.sanjay.pocketmind.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale: Material 3's scale, tuned for an app whose main content is long answers.
 *
 * - Body text gets a slightly taller line height (reading comfort for multi-paragraph answers).
 * - Titles and headlines use SemiBold, so screen and section titles stand out clearly from body
 *   text without needing a different font (no font files: smaller APK, no extra licence).
 * - Every size is in `sp`, so text follows the user's font-size setting (up to 200 %).
 */
private val base = Typography()

val PocketMindTypography = Typography(
    displayLarge = base.displayLarge,
    displayMedium = base.displayMedium,
    displaySmall = base.displaySmall,
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge,
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)
