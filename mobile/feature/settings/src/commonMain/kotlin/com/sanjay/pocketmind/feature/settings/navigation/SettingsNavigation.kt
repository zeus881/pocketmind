package com.sanjay.pocketmind.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.sanjay.pocketmind.feature.settings.SettingsNavigation
import com.sanjay.pocketmind.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the settings screen. */
@Serializable
data object SettingsRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

fun NavGraphBuilder.settingsScreen(navigation: SettingsNavigation) {
    composable<SettingsRoute> {
        SettingsScreen(navigation = navigation)
    }
}
