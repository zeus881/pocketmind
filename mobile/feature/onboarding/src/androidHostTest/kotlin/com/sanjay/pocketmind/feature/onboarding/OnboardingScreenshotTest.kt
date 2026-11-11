package com.sanjay.pocketmind.feature.onboarding

import com.sanjay.pocketmind.core.testing.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
/** Golden images of onboarding's first page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenshotTest {
    @Test
    fun firstPage() = captureScreenshots("onboarding") {
        OnboardingContent(
            state = OnboardingUiState(
                isLoading = false,
                deviceRamGb = 8,
                recommendedName = "Gemma 4 E2B",
                recommendedSizeBytes = 2_588_147_712,
            ),
            onGetStarted = {},
        )
    }
}
