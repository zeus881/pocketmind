package com.manjit.pocketmind.feature.documents.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.documents.DocumentsScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the documents tab (summarize; "Ask your PDF" from phase 7). */
@Serializable
data object DocumentsRoute

fun NavController.navigateToDocuments(navOptions: NavOptions? = null) = navigate(DocumentsRoute, navOptions)

fun NavGraphBuilder.documentsScreen() {
    composable<DocumentsRoute> {
        DocumentsScreen()
    }
}
