package com.manjit.pocketmind.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.manjit.pocketmind.debug.navigation.DebugLlmRoute
import com.manjit.pocketmind.debug.navigation.debugLlmScreen
import com.manjit.pocketmind.feature.benchmark.navigation.benchmarkScreen
import com.manjit.pocketmind.feature.chat.navigation.chatScreen
import com.manjit.pocketmind.feature.documents.navigation.documentsScreen
import com.manjit.pocketmind.feature.history.navigation.historyScreen
import com.manjit.pocketmind.feature.models.navigation.modelsScreen
import com.manjit.pocketmind.feature.onboarding.navigation.onboardingScreen
import com.manjit.pocketmind.feature.settings.navigation.settingsScreen
import com.manjit.pocketmind.feature.translate.navigation.translateScreen
import com.manjit.pocketmind.navigation.TopLevelDestination

/**
 * The app's outer frame: bottom navigation bar + the NavHost that swaps screens.
 *
 * The NavHost is where all features meet. Features never navigate to each other directly;
 * they expose callbacks, and this file connects those callbacks to NavController calls.
 */
@Composable
fun PocketMindAppUi(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            // Phase 2: start on the engine test screen so the model can be tried on a phone.
            startDestination = DebugLlmRoute,
            modifier = Modifier.padding(padding),
        ) {
            debugLlmScreen()
            chatScreen()
            historyScreen()
            documentsScreen()
            translateScreen()
            settingsScreen()
            modelsScreen()
            benchmarkScreen()
            onboardingScreen()
        }
    }
}

/**
 * Standard bottom-tab behaviour: one copy of each tab on the back stack, and each tab
 * remembers its own scroll position/state when you switch away and back.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(
        destination.route,
        navOptions {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        },
    )
}
