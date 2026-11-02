package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptics with names that say WHAT happened, so every screen uses the same feel for the same
 * event: a crisp "confirm" when a message is sent, a "reject" for an error, a light tick for
 * toggles. The phone's own haptic settings still apply (users who turned vibration off feel
 * nothing).
 */
class PmHaptics internal constructor(private val haptic: HapticFeedback) {
    /** A successful action: message sent, model ready, file verified. */
    fun confirm() = haptic.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something didn't work: an error appears. */
    fun reject() = haptic.performHapticFeedback(HapticFeedbackType.Reject)

    /** A switch or choice changed. */
    fun toggle(on: Boolean) =
        haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** A long press opened a menu (e.g. on a message). */
    fun longPress() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberPmHaptics(): PmHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { PmHaptics(haptic) }
}
