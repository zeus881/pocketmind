package com.sanjay.pocketmind.feature.history.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.window.core.layout.WindowSizeClass
import com.sanjay.pocketmind.feature.history.HistoryScreen
import kotlinx.serialization.Serializable

/** Type-safe route for the history screen. */
@Serializable
data object HistoryRoute

fun NavController.navigateToHistory(navOptions: NavOptions? = null) = navigate(HistoryRoute, navOptions)

/**
 * [onOpenChat] is how history asks to show a chat. The history module doesn't know the chat
 * module exists; the app module turns this callback into navigation.
 *
 * [detailPane] is a SLOT for wide windows (tablets, unfolded foldables, landscape): there the
 * list and the selected chat are shown side by side ("list-detail"). The app fills the slot
 * with the chat screen, so history still never depends on the chat module. On narrow windows
 * the slot is unused and tapping a chat navigates as before.
 */
fun NavGraphBuilder.historyScreen(
    onOpenChat: (String) -> Unit,
    detailPane: (@Composable (chatId: String?) -> Unit)? = null,
) {
    composable<HistoryRoute> {
        val wide = currentWindowAdaptiveInfoV2().windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
        if (detailPane == null || !wide) {
            HistoryScreen(onOpenChat = onOpenChat)
        } else {
            HistoryListDetail(detailPane)
        }
    }
}

/** List on the start side (40 %), the selected chat on the end side; the selection survives rotation. */
@Composable
private fun HistoryListDetail(detailPane: @Composable (chatId: String?) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize()) {
        HistoryScreen(onOpenChat = { selected = it }, modifier = Modifier.weight(LIST_WEIGHT))
        VerticalDivider()
        Box(Modifier.weight(1f - LIST_WEIGHT)) { detailPane(selected) }
    }
}

private const val LIST_WEIGHT = 0.4f
