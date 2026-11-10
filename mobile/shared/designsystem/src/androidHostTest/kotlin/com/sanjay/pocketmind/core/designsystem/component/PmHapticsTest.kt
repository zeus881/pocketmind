package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each app event maps to one fixed haptic type, so the same action always feels the same. */
class PmHapticsTest {
    private val performed = mutableListOf<HapticFeedbackType>()
    private val haptics = PmHaptics(
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performed += hapticFeedbackType
            }
        },
    )

    @Test
    fun `events map to their haptic types`() {
        haptics.confirm()
        haptics.reject()
        haptics.toggle(on = true)
        haptics.toggle(on = false)
        haptics.longPress()

        assertEquals(
            listOf(
                HapticFeedbackType.Confirm,
                HapticFeedbackType.Reject,
                HapticFeedbackType.ToggleOn,
                HapticFeedbackType.ToggleOff,
                HapticFeedbackType.LongPress,
            ),
            performed,
        )
    }
}
