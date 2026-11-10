package com.sanjay.pocketmind.feature.chat.component

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.designsystem.component.MarkdownText
import com.sanjay.pocketmind.core.designsystem.component.StreamingMarkdownText
import com.sanjay.pocketmind.core.designsystem.component.rememberPmHaptics
import com.sanjay.pocketmind.core.designsystem.theme.PmBubbleShapes
import com.sanjay.pocketmind.core.designsystem.theme.PmMotion
import com.sanjay.pocketmind.core.designsystem.theme.PmSize
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.core.tools.CalculatorTool
import com.sanjay.pocketmind.core.tools.DateTimeTool
import com.sanjay.pocketmind.core.tools.NotesTool
import com.sanjay.pocketmind.core.tools.UnitConverterTool
import com.sanjay.pocketmind.feature.chat.R
import kotlinx.coroutines.launch

private const val STREAMING_KEY = "streaming"

/**
 * The scrolling conversation, oldest message at the top, scrolled like current AI assistant apps
 * (see [ChatScrollEffect]): a new question moves to the top of the screen and its answer is written
 * downwards below it; nothing scrolls on its own after that.
 *
 * To make that possible even for a short answer, the newest answer is at least one screen tall minus
 * its question ([newestAnswerMinHeight]); the empty space sits below the answer, where the next
 * question will go. History (from the 2026-11-03 phone tests): the old `reverseLayout = true` list
 * was anchored to the bottom of the newest item, so every new line pushed the text up and the
 * answer appeared at the bottom of the screen.
 *
 * v2 design: user messages are bubbles on the end side; answers are plain, full-width text
 * (like current AI assistants), which gives code blocks and tables room. On wide windows the
 * conversation is centred at a readable width, but the WHOLE area still scrolls (the side
 * margins are list padding, not a narrower list). Messages fade in as they arrive
 * (`animateItem`), and a "jump to latest" button appears while there is more below (AUDIT L-7).
 *
 * Stable `key`s let Compose keep each item's state when the list changes, instead of
 * re-creating every message on every new one. Items fade in and out but have NO placement
 * animation: the answer being written grows every 50 ms, and a spring on every change made the
 * list shimmer while typing (seen on the phone).
 */
@Composable
internal fun MessageList(
    messages: List<Message>,
    streamingText: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    branches: Map<String, BranchInfo> = emptyMap(),
    onEdit: (messageId: String, newText: String) -> Unit = { _, _ -> },
    onShowVersion: (messageId: String, offset: Int) -> Unit = { _, _ -> },
    speakingMessageId: String? = null,
    onToggleSpeak: (messageId: String, text: String) -> Unit = { _, _ -> },
) {
    // Editing and switching versions are blocked while an answer is being written (same rule as retry).
    val canChange = canRetry
    val lastAssistantId = messages.lastOrNull()?.takeIf { it.role == TurnRole.ASSISTANT }?.id
    val lastIndex = messages.size - if (streamingText == null) 1 else 0
    val questionIndex = messages.indexOfLast { it.role == TurnRole.USER }
    val questionId = messages.getOrNull(questionIndex)?.id
    // The answer to the newest question: the one being written, or the saved one right after it.
    val newestAnswerId = when {
        streamingText != null -> STREAMING_KEY
        questionIndex >= 0 && questionIndex == messages.size - 2 -> lastAssistantId
        else -> null
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = lastIndex.coerceAtLeast(0))
    ChatScrollEffect(listState, questionIndex, questionId)
    val scope = rememberCoroutineScope()
    val showJump by remember { derivedStateOf { listState.canScrollForward } }
    // The newest question's height, measured; until then 0, which only makes the answer's space bigger.
    var questionHeight by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideMargin = maxOf(PmSpacing.l, (maxWidth - PmSize.readableContentWidth) / 2)
        val measuredQuestion = questionHeight?.takeIf { it.first == questionId }?.second ?: 0
        val answerMinHeight = newestAnswerMinHeight(maxHeight, with(density) { measuredQuestion.toDp() })
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sideMargin, vertical = PmSpacing.m),
            verticalArrangement = Arrangement.spacedBy(PmSpacing.l),
        ) {
            items(messages, key = { it.id }) { message ->
                val versions = branches[message.id]
                Column(
                    Modifier
                        .animateItem(placementSpec = null)
                        .then(if (message.id == newestAnswerId) Modifier.heightIn(min = answerMinHeight) else Modifier)
                        .then(
                            if (message.id == questionId) {
                                Modifier.onSizeChanged { questionHeight = message.id to it.height }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (message.role == TurnRole.USER) {
                        message.imagePath?.let { path ->
                            PhotoThumbnail(
                                path = path,
                                size = PmSize.photoInBubble,
                                contentDescription = stringResource(R.string.feature_chat_attached_photo),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentWidth(Alignment.End)
                                    .padding(horizontal = PmSpacing.l, vertical = PmSpacing.xs),
                            )
                        }
                        UserBubble(message.text, canEdit = canChange, onEdit = { onEdit(message.id, it) })
                    } else {
                        AssistantAnswer(
                            message = message,
                            showRetry = canRetry && message.id == lastAssistantId,
                            onRetry = onRetry,
                            speaking = message.id == speakingMessageId,
                            onToggleSpeak = { onToggleSpeak(message.id, message.text) },
                        )
                    }
                    if (versions != null) {
                        VersionSwitcher(
                            info = versions,
                            enabled = canChange,
                            onShow = { offset -> onShowVersion(message.id, offset) },
                            modifier = Modifier.align(
                                if (message.role == TurnRole.USER) Alignment.End else Alignment.Start,
                            ),
                        )
                    }
                }
            }
            if (streamingText != null) {
                item(key = STREAMING_KEY) {
                    StreamingAnswer(
                        streamingText,
                        Modifier
                            .animateItem(placementSpec = null)
                            .heightIn(min = answerMinHeight),
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
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(lastIndex.coerceAtLeast(0))
                        listState.scrollToEnd()
                    }
                },
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.feature_chat_jump_latest),
                )
            }
        }
    }
}

/**
 * The newest answer fills the screen below its question, so the question can be scrolled to the top:
 * the list height minus the list padding, the gap between items and the question itself.
 */
private fun newestAnswerMinHeight(listHeight: Dp, questionHeight: Dp): Dp =
    (listHeight - PmSpacing.m * 2 - PmSpacing.l - questionHeight).coerceAtLeast(Dp.Hairline) // never negative

/**
 * A question. Long-press (or TalkBack's "actions" menu) offers Edit and Copy. Editing sends a
 * NEW version of the question; the old one stays reachable through the version switcher.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(text: String, canEdit: Boolean, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberPmHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val optionsLabel = stringResource(R.string.feature_chat_message_options)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = PmBubbleShapes.user,
                modifier = Modifier
                    .widthIn(max = PmSize.userBubbleMaxWidth)
                    .clip(PmBubbleShapes.user)
                    .combinedClickable(
                        onClick = {},
                        onLongClickLabel = optionsLabel,
                        onLongClick = {
                            haptics.longPress()
                            menuOpen = true
                        },
                    ),
            ) {
                Text(
                    text,
                    Modifier.padding(horizontal = PmSpacing.l, vertical = PmSpacing.m),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_chat_edit)) },
                    enabled = canEdit,
                    onClick = {
                        menuOpen = false
                        editing = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_chat_copy)) },
                    onClick = {
                        menuOpen = false
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("question", text))) }
                    },
                )
            }
        }
    }
    if (editing) {
        EditQuestionDialog(
            initial = text,
            onSend = { newText ->
                editing = false
                onEdit(newText)
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun EditQuestionDialog(initial: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_chat_edit_title)) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, maxLines = 8) },
        confirmButton = {
            TextButton(onClick = { onSend(value) }, enabled = value.isNotBlank() && value.trim() != initial.trim()) {
                Text(stringResource(R.string.feature_chat_edit_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_chat_cancel)) } },
    )
}

/** "‹ 2 / 3 ›": switch between versions of an edited question or a regenerated answer. */
@Composable
private fun VersionSwitcher(info: BranchInfo, enabled: Boolean, onShow: (Int) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.feature_chat_version_description, info.index + 1, info.count)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShow(-1) }, enabled = enabled && info.index > 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.feature_chat_previous_version),
            )
        }
        Text(
            text = stringResource(R.string.feature_chat_version, info.index + 1, info.count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = description },
        )
        IconButton(onClick = { onShow(1) }, enabled = enabled && info.index < info.count - 1) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.feature_chat_next_version),
            )
        }
    }
}

@Composable
private fun AssistantAnswer(
    message: Message,
    showRetry: Boolean,
    onRetry: () -> Unit,
    speaking: Boolean = false,
    onToggleSpeak: () -> Unit = {},
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        // immediate: a finished answer is parsed once, synchronously, so it never shows
        // an empty frame when the streaming answer is swapped for the saved one.
        if (message.text.isNotBlank()) MarkdownText(message.text, immediate = true)
        when (message.status) {
            MessageStatus.STOPPED -> StatusLabel(stringResource(R.string.feature_chat_status_stopped))
            MessageStatus.FAILED -> StatusLabel(stringResource(R.string.feature_chat_status_failed))
            MessageStatus.COMPLETE -> Unit
        }
        if (message.toolsUsed.isNotEmpty()) ToolsUsedLabel(message.toolsUsed)
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
            if (message.text.isNotBlank()) {
                IconButton(onClick = onToggleSpeak) {
                    Icon(
                        if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = stringResource(
                            if (speaking) R.string.feature_chat_stop_reading else R.string.feature_chat_read_aloud,
                        ),
                    )
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
            StreamingMarkdownText(text)
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

/**
 * "Used: Calculator, Unit converter": shows that a number came from a tool, not from the model's
 * guess (the user can trust it more, and it explains a short pause before the answer).
 */
@Composable
private fun ToolsUsedLabel(tools: List<String>) {
    val names = tools.map { stringResource(toolNameRes(it)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = PmSpacing.s, top = PmSpacing.xs),
    ) {
        Icon(
            Icons.Outlined.Calculate,
            contentDescription = null, // the text says it
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(PmSpacing.l),
        )
        Text(
            text = stringResource(R.string.feature_chat_tools_used, names.joinToString(", ")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = PmSpacing.xs),
        )
    }
}

private fun toolNameRes(name: String): Int = when (name) {
    CalculatorTool.NAME -> R.string.feature_chat_tool_calculator
    DateTimeTool.NAME -> R.string.feature_chat_tool_date
    UnitConverterTool.NAME -> R.string.feature_chat_tool_units
    NotesTool.NAME -> R.string.feature_chat_tool_notes
    else -> R.string.feature_chat_tool_other
}
