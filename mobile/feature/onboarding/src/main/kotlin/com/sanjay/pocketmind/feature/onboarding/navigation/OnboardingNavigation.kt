package com.sanjay.pocketmind.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sanjay.pocketmind.feature.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the first-launch onboarding. */
@Serializable
data object OnboardingRoute

fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit) {
    composable<OnboardingRoute> {
        OnboardingScreen(onFinished = onFinished)
    }
}
