package com.sanjay.pocketmind.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.sanjay.pocketmind.feature.history.HistoryScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the history screen. */
@Serializable
data object HistoryRoute

fun NavController.navigateToHistory(navOptions: NavOptions? = null) = navigate(HistoryRoute, navOptions)

/**
 * [onOpenChat] is how history asks to show a chat. The history module doesn't know the chat
 * module exists; the app module turns this callback into navigation.
 */
fun NavGraphBuilder.historyScreen(onOpenChat: (String) -> Unit) {
    composable<HistoryRoute> {
        HistoryScreen(onOpenChat = onOpenChat)
    }
}
