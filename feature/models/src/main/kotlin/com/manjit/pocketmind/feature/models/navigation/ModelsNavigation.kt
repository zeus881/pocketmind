package com.manjit.pocketmind.feature.models.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.models.ModelsScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Models screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object ModelsRoute

fun NavController.navigateToModels(navOptions: NavOptions? = null) = navigate(ModelsRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.modelsScreen() {
    composable<ModelsRoute> {
        ModelsScreen()
    }
}
