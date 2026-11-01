package com.manjit.pocketmind.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Settings screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object SettingsRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.settingsScreen() {
    composable<SettingsRoute> {
        SettingsScreen()
    }
}
