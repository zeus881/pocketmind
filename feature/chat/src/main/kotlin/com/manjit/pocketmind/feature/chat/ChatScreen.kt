package com.manjit.pocketmind.feature.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Chat screen. Its real content is built in a later phase. */
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.AutoMirrored.Outlined.Chat,
        title = stringResource(R.string.feature_chat_title),
        modifier = modifier,
    )
}
