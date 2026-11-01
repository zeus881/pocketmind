package com.manjit.pocketmind.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.chat.ChatScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Chat screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object ChatRoute

fun NavController.navigateToChat(navOptions: NavOptions? = null) = navigate(ChatRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.chatScreen() {
    composable<ChatRoute> {
        ChatScreen()
    }
}
