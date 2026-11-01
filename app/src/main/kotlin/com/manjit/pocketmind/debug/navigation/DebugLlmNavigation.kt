package com.manjit.pocketmind.debug.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.manjit.pocketmind.debug.DebugLlmScreen
import kotlinx.serialization.Serializable

/** Route of the temporary engine test screen (removed in phase 4). */
@Serializable
data object DebugLlmRoute

fun NavGraphBuilder.debugLlmScreen() {
    composable<DebugLlmRoute> { DebugLlmScreen() }
}
