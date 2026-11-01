package com.manjit.pocketmind.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.chat.ChatScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the chat screen. [chatId] = null means "a new, not-yet-saved chat".
 * Navigation stores the argument in the ViewModel's SavedStateHandle under the key "chatId",
 * so it survives process death (Android killing the app in the background).
 */
@Serializable
data class ChatRoute(val chatId: String? = null)

fun NavController.navigateToChat(chatId: String?, navOptions: NavOptions? = null) =
    navigate(ChatRoute(chatId), navOptions)

/** Registers the chat screen. Navigation to other features is passed in as callbacks. */
fun NavGraphBuilder.chatScreen(onOpenModels: () -> Unit) {
    composable<ChatRoute> {
        ChatScreen(onOpenModels = onOpenModels)
    }
}
