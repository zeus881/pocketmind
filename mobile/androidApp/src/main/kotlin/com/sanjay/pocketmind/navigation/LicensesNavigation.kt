package com.sanjay.pocketmind.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sanjay.pocketmind.ui.LicensesScreen
import kotlinx.serialization.Serializable

/** Route of the open-source licences screen (lives in :app, where the licence data is generated). */
@Serializable
data object LicensesRoute

fun NavGraphBuilder.licensesScreen(onBack: () -> Unit) {
    composable<LicensesRoute> { LicensesScreen(onBack) }
}
