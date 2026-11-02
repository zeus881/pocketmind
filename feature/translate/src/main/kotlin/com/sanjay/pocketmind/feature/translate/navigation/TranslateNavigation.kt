package com.sanjay.pocketmind.feature.translate.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.sanjay.pocketmind.feature.translate.TranslateScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Translate screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object TranslateRoute

fun NavController.navigateToTranslate(navOptions: NavOptions? = null) = navigate(TranslateRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.translateScreen() {
    composable<TranslateRoute> {
        TranslateScreen()
    }
}
