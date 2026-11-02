package com.sanjay.pocketmind.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.EmptyState
import com.sanjay.pocketmind.core.designsystem.component.ErrorState
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.feature.chat.component.ChatInput
import com.sanjay.pocketmind.feature.chat.component.MessageList
import kotlinx.coroutines.delay

/**
 * Stateful wrapper: gets the ViewModel from Hilt, collects its state and events, and passes
 * plain values + callbacks down to the stateless [ChatContent].
 *
 * Splitting "stateful screen" from "stateless content" is a Compose best practice: the
 * content can be previewed and UI-tested with any state, without a ViewModel.
 */
@Composable
fun ChatScreen(onOpenModels: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    // collectAsStateWithLifecycle stops collecting while the app is in the background.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources (not LocalContext) so strings follow configuration changes like language.
    val resources = LocalResources.current

    // LaunchedEffect(Unit) starts once when the screen appears and is cancelled when it leaves.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.Error -> {
                    // The action must be able to fix the problem: retrying can't create a missing model.
                    val noModel = event.kind == ErrorKind.NO_MODEL
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(event.kind.messageRes()),
                        actionLabel = resources.getString(
                            if (noModel) R.string.feature_chat_get_model else R.string.feature_chat_retry,
                        ),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        if (noModel) onOpenModels() else viewModel.retry()
                    }
                }
            }
        }
    }

    ChatContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRetry = viewModel::retry,
        onNewChat = viewModel::newChat,
        onReloadModel = viewModel::retryModel,
        onOpenModels = onOpenModels,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onNewChat: () -> Unit,
    onReloadModel: () -> Unit,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val newChat = stringResource(R.string.feature_chat_new_chat)
                    Text(
                        text = state.title?.takeIf { it.isNotBlank() } ?: newChat,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            Icons.Outlined.AddComment,
                            contentDescription = stringResource(R.string.feature_chat_new_chat),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Moves the input above the keyboard (edge-to-edge apps must do this themselves).
                .imePadding(),
        ) {
            when (state.model) {
                ModelUi.NoModel -> EmptyState(
                    icon = Icons.Outlined.CloudDownload,
                    title = stringResource(R.string.feature_chat_no_model_title),
                    body = stringResource(R.string.feature_chat_no_model_body),
                    actionLabel = stringResource(R.string.feature_chat_get_model),
                    onAction = onOpenModels,
                    modifier = Modifier.weight(1f),
                )

                is ModelUi.Failed -> ErrorState(
                    message = stringResource(state.model.reason.messageRes()),
                    onRetry = onReloadModel,
                    modifier = Modifier.weight(1f),
                    // A damaged/missing file or a crash can be fixed in the model manager
                    // (check the file, download again, or pick a smaller model).
                    secondaryActionLabel = stringResource(R.string.feature_chat_open_models)
                        .takeIf { state.model.reason != FailureReason.INIT_FAILED },
                    onSecondaryAction = onOpenModels,
                )

                else -> {
                    if (state.model == ModelUi.Loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.feature_chat_loading_model),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (state.messages.isEmpty() && state.streamingText == null) {
                            EmptyState(
                                icon = Icons.AutoMirrored.Outlined.Chat,
                                title = stringResource(R.string.feature_chat_empty_title),
                                body = stringResource(R.string.feature_chat_empty_body),
                            )
                        } else {
                            MessageList(
                                messages = state.messages,
                                streamingText = state.streamingText,
                                canRetry = !state.isGenerating,
                                onRetry = onRetry,
                            )
                        }
                    }
                    GenerationStatus(isGenerating = state.isGenerating)
                    ChatInput(
                        isGenerating = state.isGenerating,
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

/** Maps a failure kind to a user-readable message. */
internal fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.NO_MODEL -> R.string.feature_chat_no_model_title
    ErrorKind.MODEL_LOAD_FAILED -> R.string.feature_chat_model_failed
    else -> R.string.feature_chat_generation_failed
}

/** User-readable text for each way the model can fail to load. */
private fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.FILE_NOT_FOUND, FailureReason.FILE_DAMAGED -> R.string.feature_chat_model_damaged
    FailureReason.INIT_FAILED -> R.string.feature_chat_model_failed
    FailureReason.CRASHED_LAST_TIME -> R.string.feature_chat_model_crashed
}

/**
 * One short status line for screen-reader users (and a quiet hint for everyone):
 * "PocketMind is writing…" while an answer streams, then "Answer ready" for a few seconds.
 *
 * WHY: v1 made the streaming bubble itself a live region. It changed every 50 ms, so TalkBack
 * kept interrupting itself (AUDIT M-3). Now TalkBack announces exactly two things: the start
 * and the end. The answer itself is read when the user moves to it.
 */
@Composable
private fun GenerationStatus(isGenerating: Boolean) {
    var showReady by remember { mutableStateOf(false) }
    var wasGenerating by remember { mutableStateOf(isGenerating) }
    LaunchedEffect(isGenerating) {
        if (wasGenerating && !isGenerating) {
            showReady = true
            delay(READY_VISIBLE_MS)
            showReady = false
        }
        wasGenerating = isGenerating
    }
    val text = when {
        isGenerating -> stringResource(R.string.feature_chat_status_writing)
        showReady -> stringResource(R.string.feature_chat_status_ready)
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private const val READY_VISIBLE_MS = 3_000L
