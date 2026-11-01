package com.manjit.pocketmind.feature.chat.component

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manjit.pocketmind.core.model.Message
import com.manjit.pocketmind.core.model.MessageStatus
import com.manjit.pocketmind.core.model.TurnRole
import com.manjit.pocketmind.feature.chat.R
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

private const val STREAMING_KEY = "streaming"

/**
 * The scrolling list of bubbles.
 *
 * `reverseLayout = true` anchors the list to the BOTTOM, like every chat app: new messages
 * appear at the bottom and the list stays scrolled there without manual scrolling code.
 * Because of that, items are given newest-first.
 *
 * Stable `key`s let Compose keep each bubble's state when the list changes, instead of
 * re-creating every bubble on every new message.
 */
@Composable
internal fun MessageList(
    messages: List<Message>,
    streamingText: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val newestFirst = messages.asReversed()
    val lastAssistantId = messages.lastOrNull()?.takeIf { it.role == TurnRole.ASSISTANT }?.id
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (streamingText != null) {
            item(key = STREAMING_KEY) { StreamingBubble(streamingText) }
        }
        items(newestFirst, key = { it.id }) { message ->
            if (message.role == TurnRole.USER) {
                UserBubble(message.text)
            } else {
                AssistantBubble(
                    message = message,
                    showRetry = canRetry && message.id == lastAssistantId,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AssistantBubble(message: Message, showRetry: Boolean, onRetry: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (message.text.isNotBlank()) Markdown(content = message.text)
                when (message.status) {
                    MessageStatus.STOPPED -> StatusLabel(stringResource(R.string.feature_chat_status_stopped))
                    MessageStatus.FAILED -> StatusLabel(stringResource(R.string.feature_chat_status_failed))
                    MessageStatus.COMPLETE -> Unit
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The "12.4 tok/s · GPU" label from CLAUDE.md, stored with the message.
            val tps = message.tokensPerSecond
            val backend = message.backend
            if (tps != null && backend != null) {
                Text(
                    text = stringResource(R.string.feature_chat_stats, tps, backend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (message.text.isNotBlank()) {
                // IconButton is 48x48 dp: the minimum touch target for accessibility.
                IconButton(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("answer", message.text))) }
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.feature_chat_copy))
                }
            }
            if (showRetry) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.feature_chat_regenerate))
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
        // Polite live region: TalkBack reads the answer as it grows, without interrupting.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(12.dp)) {
            if (text.isEmpty()) {
                Text(stringResource(R.string.feature_chat_thinking), style = MaterialTheme.typography.bodyLarge)
            } else {
                Markdown(content = text)
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp),
    )
}
