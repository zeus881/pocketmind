package com.manjit.pocketmind.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.history.HistoryScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the History screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object HistoryRoute

fun NavController.navigateToHistory(navOptions: NavOptions? = null) = navigate(HistoryRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.historyScreen() {
    composable<HistoryRoute> {
        HistoryScreen()
    }
}
