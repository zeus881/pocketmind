package com.manjit.pocketmind.feature.models.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.manjit.pocketmind.feature.models.ModelsScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the model manager. */
@Serializable
data object ModelsRoute

fun NavController.navigateToModels(navOptions: NavOptions? = null) = navigate(ModelsRoute, navOptions)

fun NavGraphBuilder.modelsScreen(onBack: () -> Unit) {
    composable<ModelsRoute> {
        ModelsScreen(onBack = onBack)
    }
}
