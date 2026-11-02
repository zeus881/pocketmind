package com.sanjay.pocketmind.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.sanjay.pocketmind.R
import com.sanjay.pocketmind.feature.chat.navigation.ChatRoute
import com.sanjay.pocketmind.feature.documents.navigation.DocumentsRoute
import com.sanjay.pocketmind.feature.history.navigation.HistoryRoute
import com.sanjay.pocketmind.feature.settings.navigation.SettingsRoute
import com.sanjay.pocketmind.feature.translate.navigation.TranslateRoute
import kotlin.reflect.KClass

/**
 * The tabs in the bottom navigation bar. Each one points at a feature's type-safe route.
 * Only the app module knows all features, so only the app module builds this list.
 */
enum class TopLevelDestination(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val route: Any,
    val routeClass: KClass<*>,
) {
    CHAT(Icons.AutoMirrored.Outlined.Chat, R.string.nav_chat, ChatRoute(), ChatRoute::class),
    HISTORY(Icons.Outlined.History, R.string.nav_history, HistoryRoute, HistoryRoute::class),
    DOCUMENTS(Icons.Outlined.Description, R.string.nav_documents, DocumentsRoute, DocumentsRoute::class),
    TRANSLATE(Icons.Outlined.Translate, R.string.nav_translate, TranslateRoute, TranslateRoute::class),
    SETTINGS(Icons.Outlined.Settings, R.string.nav_settings, SettingsRoute, SettingsRoute::class),
}
