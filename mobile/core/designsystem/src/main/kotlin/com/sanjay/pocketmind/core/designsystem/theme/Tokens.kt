package com.sanjay.pocketmind.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Design tokens: the named values every screen uses instead of raw numbers.
 *
 * WHY: with "16.dp" typed in 40 places, a spacing change means 40 edits and the screens slowly
 * drift apart (15 here, 18 there). With tokens, the whole app follows one 4 dp grid, and a
 * design change is one line. The build check `checkDesignTokens` fails if a screen uses a raw
 * dp value or colour, so the system can't erode.
 */

/** Spacing on a 4 dp grid. Names describe size, not use, so they work anywhere. */
object PmSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Fixed sizes of recurring elements. */
object PmSize {
    /** Minimum touch target (Material + Android accessibility guideline). */
    val touchTarget = 48.dp

    /** Icon inside an empty/error illustration. */
    val illustrationIcon = 40.dp

    /** The whole illustration (layered circles + icon). */
    val illustration = 120.dp

    /** Big icon on full-screen states such as the lock screen. */
    val heroIcon = 64.dp

    /** A user's chat bubble never spans the full width: easier to tell apart from answers. */
    val userBubbleMaxWidth = 320.dp

    /** Content width cap on large screens (tablets, foldables) so lines stay readable. */
    val readableContentWidth = 720.dp

    /** Preview of a photo attached in the chat input. */
    val photoChip = 64.dp

    /** Longest side of a photo shown inside a question bubble. */
    val photoInBubble = 220.dp
}

/** Corner shapes: small for chips, medium for cards, large for sheets and dialogs. */
val PocketMindShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Chat bubbles: the "tail" corner points toward the speaker's side. */
object PmBubbleShapes {
    val user = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp)
    val assistant = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp)
}

/**
 * Motion tokens (Material 3 motion guidance). Durations in milliseconds; "emphasized"
 * curves for elements entering the screen, "standard" for small state changes.
 */
object PmMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 300
    const val LONG_MS = 500

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
