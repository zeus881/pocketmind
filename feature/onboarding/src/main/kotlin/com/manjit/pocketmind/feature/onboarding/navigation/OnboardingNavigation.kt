package com.manjit.pocketmind.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Welcome screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object OnboardingRoute

fun NavController.navigateToOnboarding(navOptions: NavOptions? = null) = navigate(OnboardingRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.onboardingScreen() {
    composable<OnboardingRoute> {
        OnboardingScreen()
    }
}
