package com.manjit.pocketmind.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.manjit.pocketmind.feature.benchmark.navigation.benchmarkScreen
import com.manjit.pocketmind.feature.benchmark.navigation.navigateToBenchmark
import com.manjit.pocketmind.feature.chat.navigation.ChatRoute
import com.manjit.pocketmind.feature.chat.navigation.chatScreen
import com.manjit.pocketmind.feature.chat.navigation.navigateToChat
import com.manjit.pocketmind.feature.documents.navigation.documentsScreen
import com.manjit.pocketmind.feature.history.navigation.historyScreen
import com.manjit.pocketmind.feature.models.navigation.modelsScreen
import com.manjit.pocketmind.feature.models.navigation.navigateToModels
import com.manjit.pocketmind.feature.onboarding.navigation.OnboardingRoute
import com.manjit.pocketmind.feature.onboarding.navigation.onboardingScreen
import com.manjit.pocketmind.feature.settings.SettingsNavigation
import com.manjit.pocketmind.feature.settings.navigation.settingsScreen
import com.manjit.pocketmind.feature.translate.navigation.translateScreen
import com.manjit.pocketmind.navigation.LicensesRoute
import com.manjit.pocketmind.navigation.TopLevelDestination
import com.manjit.pocketmind.navigation.licensesScreen

/**
 * The app's outer frame: bottom navigation bar + the NavHost that swaps screens.
 *
 * The NavHost is where all features meet. Features never navigate to each other directly;
 * they expose callbacks, and this file connects those callbacks to NavController calls.
 */
@Composable
fun PocketMindAppUi(startDestination: Any, navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // The bottom bar belongs to the five tabs only, not to onboarding or the model manager.
    val onTopLevel = TopLevelDestination.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }

    Scaffold(
        // Each screen draws its own top bar (which handles the status bar). The outer frame
        // only reserves room for the bottom navigation bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTopLevel) {
                BottomBar(navController, currentDestination)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                // Tell inner screens the bottom-bar space is already handled, so the chat's
                // imePadding() doesn't add it a second time when the keyboard opens.
                .consumeWindowInsets(padding),
        ) {
            onboardingScreen(
                onFinished = {
                    // After onboarding: pick a model. Onboarding is removed from the back stack,
                    // and Chat sits below Models so "back" lands in the chat.
                    navController.navigate(ChatRoute()) { popUpTo<OnboardingRoute> { inclusive = true } }
                    navController.navigateToModels()
                },
            )
            chatScreen(onOpenModels = { navController.navigateToModels() })
            historyScreen(onOpenChat = { chatId -> navController.navigateToChat(chatId) })
            documentsScreen(onOpenModels = { navController.navigateToModels() })
            translateScreen()
            settingsScreen(
                SettingsNavigation(
                    onOpenModels = { navController.navigateToModels() },
                    onOpenBenchmark = { navController.navigateToBenchmark() },
                    onOpenLicenses = { navController.navigate(LicensesRoute) },
                ),
            )
            licensesScreen(onBack = { navController.popBackStack() })
            modelsScreen(onBack = { navController.popBackStack() })
            benchmarkScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentDestination: NavDestination?) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                // The label below already names the tab, so the icon is decorative for TalkBack.
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
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
