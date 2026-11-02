package com.sanjay.pocketmind.feature.chat.component

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.designsystem.component.MarkdownText
import com.sanjay.pocketmind.core.designsystem.theme.PmBubbleShapes
import com.sanjay.pocketmind.core.designsystem.theme.PmMotion
import com.sanjay.pocketmind.core.designsystem.theme.PmSize
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.feature.chat.R
import kotlinx.coroutines.launch

private const val STREAMING_KEY = "streaming"

/**
 * The scrolling conversation.
 *
 * `reverseLayout = true` anchors the list to the BOTTOM, like every chat app: new messages
 * appear at the bottom and the list stays scrolled there without manual scrolling code.
 * Because of that, items are given newest-first, and item 0 is the newest.
 *
 * v2 design: user messages are bubbles on the end side; answers are plain, full-width text
 * (like current AI assistants), which gives code blocks and tables room. On wide windows the
 * conversation is centred at a readable width, but the WHOLE area still scrolls (the side
 * margins are list padding, not a narrower list). Messages fade and slide in as they arrive
 * (`animateItem`), and a "jump to latest" button appears after scrolling up (AUDIT L-7).
 *
 * Stable `key`s let Compose keep each item's state when the list changes, instead of
 * re-creating every message on every new one.
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideMargin = maxOf(PmSpacing.l, (maxWidth - PmSize.readableContentWidth) / 2)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = sideMargin, vertical = PmSpacing.m),
            verticalArrangement = Arrangement.spacedBy(PmSpacing.l),
        ) {
            if (streamingText != null) {
                item(key = STREAMING_KEY) { StreamingAnswer(streamingText, Modifier.animateItem()) }
            }
            items(newestFirst, key = { it.id }) { message ->
                if (message.role == TurnRole.USER) {
                    UserBubble(message.text, Modifier.animateItem())
                } else {
                    AssistantAnswer(
                        message = message,
                        showRetry = canRetry && message.id == lastAssistantId,
                        onRetry = onRetry,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showJump,
            enter = fadeIn(tween(PmMotion.SHORT_MS)) + scaleIn(tween(PmMotion.SHORT_MS, easing = PmMotion.standard)),
            exit = fadeOut(tween(PmMotion.SHORT_MS)) + scaleOut(tween(PmMotion.SHORT_MS, easing = PmMotion.standard)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(PmSpacing.l),
        ) {
            SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.feature_chat_jump_latest),
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = PmBubbleShapes.user,
            modifier = Modifier.widthIn(max = PmSize.userBubbleMaxWidth),
        ) {
            Text(
                text,
                Modifier.padding(horizontal = PmSpacing.l, vertical = PmSpacing.m),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AssistantAnswer(message: Message, showRetry: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        // immediate: a finished answer is parsed once, synchronously, so it never shows
        // an empty frame when the streaming answer is swapped for the saved one.
        if (message.text.isNotBlank()) MarkdownText(message.text, immediate = true)
        when (message.status) {
            MessageStatus.STOPPED -> StatusLabel(stringResource(R.string.feature_chat_status_stopped))
            MessageStatus.FAILED -> StatusLabel(stringResource(R.string.feature_chat_status_failed))
            MessageStatus.COMPLETE -> Unit
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
                    modifier = Modifier.padding(start = PmSpacing.s),
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

/**
 * The answer being written. Full width from the first token, so it grows only downwards and
 * never jumps sideways. No live region here: the chat screen's status line announces start and
 * end once (AUDIT M-3).
 */
@Composable
private fun StreamingAnswer(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (text.isEmpty()) {
            Text(
                stringResource(R.string.feature_chat_thinking),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MarkdownText(text)
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = PmSpacing.xs),
    )
}
