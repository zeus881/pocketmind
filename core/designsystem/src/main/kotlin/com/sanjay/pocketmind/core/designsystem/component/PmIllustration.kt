package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.sanjay.pocketmind.core.designsystem.theme.PmMotion
import com.sanjay.pocketmind.core.designsystem.theme.PmSize

/**
 * The illustration used by every empty and error state: a soft backdrop, two accent dots, a
 * front disc and the state's icon, all drawn in code from the current theme colours.
 *
 * WHY drawn, not image files: it follows light/dark mode and the user's dynamic colours
 * automatically, costs a few bytes instead of one PNG per density, and stays sharp at any
 * size or font scale. It is decorative (the title next to it says the same), so it is
 * invisible to TalkBack.
 *
 * It "settles" in with a short scale animation (emphasized-decelerate, 300 ms), which makes a
 * screen feel responsive instead of static. The animation runs once per appearance.
 */
@Composable
fun PmIllustration(icon: ImageVector, modifier: Modifier = Modifier, tone: IllustrationTone = IllustrationTone.Brand) {
    val colors = MaterialTheme.colorScheme
    val palette = when (tone) {
        IllustrationTone.Brand -> IllustrationPalette(
            backdrop = colors.primaryContainer.copy(alpha = BACKDROP_ALPHA),
            accent = colors.tertiaryContainer,
            disc = colors.primaryContainer,
            icon = colors.onPrimaryContainer,
        )

        IllustrationTone.Error -> IllustrationPalette(
            backdrop = colors.errorContainer.copy(alpha = BACKDROP_ALPHA),
            accent = colors.secondaryContainer,
            disc = colors.errorContainer,
            icon = colors.onErrorContainer,
        )
    }
    val scale = remember { Animatable(START_SCALE) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(PmMotion.MEDIUM_MS, easing = PmMotion.emphasizedDecelerate))
    }
    Box(
        modifier = modifier
            .size(PmSize.illustration)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(PmSize.illustration)) {
            val r = size.minDimension / 2
            drawCircle(palette.backdrop, radius = r)
            drawCircle(palette.accent, radius = r * BIG_DOT, center = Offset(size.width * 0.84f, size.height * 0.2f))
            drawCircle(palette.accent, radius = r * SMALL_DOT, center = Offset(size.width * 0.14f, size.height * 0.8f))
            drawCircle(palette.disc, radius = r * DISC)
        }
        Icon(icon, contentDescription = null, tint = palette.icon, modifier = Modifier.size(PmSize.illustrationIcon))
    }
}

private data class IllustrationPalette(val backdrop: Color, val accent: Color, val disc: Color, val icon: Color)

private const val BACKDROP_ALPHA = 0.45f
private const val START_SCALE = 0.85f
private const val BIG_DOT = 0.22f
private const val SMALL_DOT = 0.13f
private const val DISC = 0.62f
