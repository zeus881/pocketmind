package com.manjit.pocketmind.feature.documents.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.documents.DocumentsScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe route for the Documents screen. Navigation Compose turns this @Serializable
 * object into a URL-like route string for us, so we never type route strings by hand.
 */
@Serializable
data object DocumentsRoute

fun NavController.navigateToDocuments(navOptions: NavOptions? = null) = navigate(DocumentsRoute, navOptions)

/** Registers the screen in the app's NavHost. The app module calls this. */
fun NavGraphBuilder.documentsScreen() {
    composable<DocumentsRoute> {
        DocumentsScreen()
    }
}
