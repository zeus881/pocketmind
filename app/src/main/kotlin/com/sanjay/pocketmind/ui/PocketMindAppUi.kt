package com.sanjay.pocketmind.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.sanjay.pocketmind.feature.benchmark.navigation.benchmarkScreen
import com.sanjay.pocketmind.feature.benchmark.navigation.navigateToBenchmark
import com.sanjay.pocketmind.feature.chat.ChatScreen
import com.sanjay.pocketmind.feature.chat.ChatViewModel
import com.sanjay.pocketmind.feature.chat.navigation.ChatRoute
import com.sanjay.pocketmind.feature.chat.navigation.chatScreen
import com.sanjay.pocketmind.feature.chat.navigation.navigateToChat
import com.sanjay.pocketmind.feature.documents.navigation.documentsScreen
import com.sanjay.pocketmind.feature.history.navigation.historyScreen
import com.sanjay.pocketmind.feature.models.navigation.modelsScreen
import com.sanjay.pocketmind.feature.models.navigation.navigateToModels
import com.sanjay.pocketmind.feature.onboarding.navigation.OnboardingRoute
import com.sanjay.pocketmind.feature.onboarding.navigation.onboardingScreen
import com.sanjay.pocketmind.feature.settings.SettingsNavigation
import com.sanjay.pocketmind.feature.settings.navigation.settingsScreen
import com.sanjay.pocketmind.feature.translate.navigation.translateScreen
import com.sanjay.pocketmind.navigation.LicensesRoute
import com.sanjay.pocketmind.navigation.TopLevelDestination
import com.sanjay.pocketmind.navigation.licensesScreen

/**
 * The app's outer frame: the navigation for the five tabs + the NavHost that swaps screens.
 *
 * Adaptive (v2): `NavigationSuiteScaffold` picks the navigation for the window, a bottom bar on
 * phones, a navigation rail on tablets, unfolded foldables and phones in landscape. It also
 * consumes that bar's window insets for the content, so screens don't add them twice.
 *
 * The NavHost is where all features meet. Features never navigate to each other directly;
 * they expose callbacks (and, for the History two-pane layout, a slot), and this file connects
 * them.
 */
@Composable
fun PocketMindAppUi(startDestination: Any, navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // The navigation belongs to the five tabs only, not to onboarding or the model manager.
    val onTopLevel = TopLevelDestination.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }
    val layoutType = if (onTopLevel) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true,
                    onClick = { navController.navigateToTopLevel(destination) },
                    // The label below already names the tab, so the icon is decorative for TalkBack.
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = startDestination) {
            onboardingScreen(
                onFinished = {
                    // After onboarding: pick a model. Onboarding is removed from the back stack,
                    // and Chat sits below Models so "back" lands in the chat.
                    navController.navigate(ChatRoute()) { popUpTo<OnboardingRoute> { inclusive = true } }
                    navController.navigateToModels()
                },
            )
            chatScreen(onOpenModels = { navController.navigateToModels() })
            historyScreen(
                onOpenChat = { chatId -> navController.navigateToChat(chatId) },
                // On wide screens History shows the selected chat beside the list.
                detailPane = { chatId ->
                    HistoryChatDetail(chatId, onOpenModels = { navController.navigateToModels() })
                },
            )
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

/**
 * The chat shown in History's detail pane. It has its own ViewModel instance (key), and switches
 * chats in place when another one is selected in the list.
 */
@Composable
private fun HistoryChatDetail(chatId: String?, onOpenModels: () -> Unit) {
    val viewModel: ChatViewModel = hiltViewModel(key = "history-detail")
    LaunchedEffect(chatId) { viewModel.openChat(chatId) }
    ChatScreen(onOpenModels = onOpenModels, viewModel = viewModel)
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
