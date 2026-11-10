package com.sanjay.pocketmind.feature.documents.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sanjay.pocketmind.feature.documents.DocumentsScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the documents tab (summarize; "Ask your PDF" from phase 7). */
@Serializable
data object DocumentsRoute

fun NavController.navigateToDocuments(navOptions: NavOptions? = null) = navigate(DocumentsRoute, navOptions)

/** A PDF shared from another app ("Share → PocketMind"): opens Documents and summarizes it. */
@Serializable
data class DocumentsShareRoute(val pdfUri: String)

fun NavGraphBuilder.documentsScreen(onOpenModels: () -> Unit) {
    composable<DocumentsRoute> {
        DocumentsScreen(onOpenModels = onOpenModels)
    }
    composable<DocumentsShareRoute> { entry ->
        DocumentsScreen(onOpenModels = onOpenModels, sharedPdfUri = entry.toRoute<DocumentsShareRoute>().pdfUri)
    }
}
