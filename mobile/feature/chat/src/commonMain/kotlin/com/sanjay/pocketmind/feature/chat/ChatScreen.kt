package com.sanjay.pocketmind.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.EmptyState
import com.sanjay.pocketmind.core.designsystem.component.ErrorState
import com.sanjay.pocketmind.core.designsystem.component.PmIllustration
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.component.rememberPmHaptics
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.speech.SpeechError
import com.sanjay.pocketmind.feature.chat.component.ChatInput
import com.sanjay.pocketmind.feature.chat.component.MessageList
import com.sanjay.pocketmind.feature.chat.resources.Res
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_answer_as
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_empty_body
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_empty_title
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_export_failed
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_export_markdown
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_export_pdf
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_exported
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_generation_failed
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_get_model
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_images_not_supported
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_loading_model
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_model_crashed
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_model_damaged
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_model_failed
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_more_options
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_new_chat
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_no_model_body
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_no_model_title
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_open_models
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_persona_default
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_photo_unreadable
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_retry
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_status_ready
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_status_writing
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_suggestion_code
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_suggestion_explain
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_suggestion_ideas
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_suggestion_write
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_tools
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_voice_failed
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_voice_nothing_heard
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_voice_permission
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_voice_unavailable
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
/**
 * Stateful wrapper: gets the ViewModel from Hilt, collects its state and events, and passes
 * plain values + callbacks down to the stateless [ChatContent].
 *
 * Splitting "stateful screen" from "stateless content" is a Compose best practice: the
 * content can be previewed and UI-tested with any state, without a ViewModel.
 */
@Composable
fun ChatScreen(onOpenModels: () -> Unit, viewModel: ChatViewModel = koinViewModel()) {
    // collectAsStateWithLifecycle stops collecting while the app is in the background.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberPmHaptics()

    // The system "Save as" screen: the user picks where; we only get permission for that one file.
    // Null where export isn't available yet (iOS, phase 2d): the export menu is then hidden.
    val saveMarkdown =
        rememberDocumentSaver(ExportFormat.MARKDOWN.mimeType) { viewModel.export(it, ExportFormat.MARKDOWN) }
    val savePdf = rememberDocumentSaver(ExportFormat.PDF.mimeType) { viewModel.export(it, ExportFormat.PDF) }

    // Voice input: the recognizer listens in the app's language; the microphone permission is
    // asked the first time (Android shows its own dialog; iOS asks when recording starts).
    val languageTag = Locale.current.toLanguageTag()
    val startVoice = rememberMicrophonePermission { viewModel.startVoice(languageTag) }
    val onMicClick = {
        if (state.voice.listening) viewModel.stopVoice() else startVoice()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    // LaunchedEffect(Unit) starts once when the screen appears and is cancelled when it leaves.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.Exported -> snackbarHostState.showSnackbar(
                    getString(Res.string.feature_chat_exported),
                )

                is ChatEvent.VoiceProblem -> snackbarHostState.showSnackbar(
                    getString(event.kind.messageRes()),
                )

                ChatEvent.PhotoUnreadable -> snackbarHostState.showSnackbar(
                    getString(Res.string.feature_chat_photo_unreadable),
                )

                is ChatEvent.Error -> {
                    haptics.reject()
                    // The action must be able to fix the problem: retrying can't create a missing
                    // model, and "Retry" (regenerate) makes no sense for a file that couldn't be saved.
                    val noModel = event.kind == ErrorKind.NO_MODEL
                    val fileError = event.kind == ErrorKind.FILE_UNREADABLE
                    // Only another model can read the photo: offer the model manager.
                    val needsVisionModel = event.kind == ErrorKind.IMAGES_NOT_SUPPORTED
                    val result = snackbarHostState.showSnackbar(
                        message = getString(event.kind.messageRes()),
                        actionLabel = when {
                            fileError -> null
                            noModel -> getString(Res.string.feature_chat_get_model)
                            needsVisionModel -> getString(Res.string.feature_chat_open_models)
                            else -> getString(Res.string.feature_chat_retry)
                        },
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        if (noModel || needsVisionModel) onOpenModels() else viewModel.retry()
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
        onEdit = viewModel::edit,
        onShowVersion = viewModel::showVersion,
        onSelectPersona = viewModel::selectPersona,
        onMicClick = onMicClick,
        onToggleSpeak = viewModel::toggleSpeak,
        onPhotoPicked = viewModel::attachPhoto,
        onRemovePhoto = viewModel::removePhoto,
        onToggleTools = viewModel::toggleTools,
        onExport = if (saveMarkdown == null || savePdf == null) {
            null
        } else {
            { format ->
                val name = exportFileName(state.title, format)
                if (format == ExportFormat.MARKDOWN) saveMarkdown(name) else savePdf(name)
            }
        },
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
    onEdit: (messageId: String, newText: String) -> Unit = { _, _ -> },
    onShowVersion: (messageId: String, offset: Int) -> Unit = { _, _ -> },
    /** Null: export isn't available on this platform yet, so the menu is hidden. */
    onExport: ((ExportFormat) -> Unit)? = {},
    onSelectPersona: (String?) -> Unit = {},
    onMicClick: () -> Unit = {},
    onToggleSpeak: (messageId: String, text: String) -> Unit = { _, _ -> },
    onPhotoPicked: (String) -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onToggleTools: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val newChat = stringResource(Res.string.feature_chat_new_chat)
                    Column {
                        Text(
                            text = state.title?.takeIf { it.isNotBlank() } ?: newChat,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.personaName?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    if (state.tools.available) ToolsSwitch(enabled = state.tools.enabled, onToggle = onToggleTools)
                    IconButton(onClick = onNewChat) {
                        Icon(
                            Icons.Outlined.AddComment,
                            contentDescription = stringResource(Res.string.feature_chat_new_chat),
                        )
                    }
                    if (state.messages.isNotEmpty() && onExport != null) ExportMenu(onExport)
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
                    title = stringResource(Res.string.feature_chat_no_model_title),
                    body = stringResource(Res.string.feature_chat_no_model_body),
                    actionLabel = stringResource(Res.string.feature_chat_get_model),
                    onAction = onOpenModels,
                    modifier = Modifier.weight(1f),
                )

                is ModelUi.Failed -> ErrorState(
                    message = stringResource(state.model.reason.messageRes()),
                    onRetry = onReloadModel,
                    modifier = Modifier.weight(1f),
                    // A damaged/missing file or a crash can be fixed in the model manager
                    // (check the file, download again, or pick a smaller model).
                    secondaryActionLabel = stringResource(Res.string.feature_chat_open_models)
                        .takeIf { state.model.reason != FailureReason.INIT_FAILED },
                    onSecondaryAction = onOpenModels,
                )

                else -> {
                    if (state.model == ModelUi.Loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(Res.string.feature_chat_loading_model),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(horizontal = PmSpacing.l, vertical = PmSpacing.xs)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (state.messages.isEmpty() && state.streamingText == null) {
                            ChatWelcome(
                                onSuggestion = onSend,
                                personas = state.personas,
                                selectedPersonaId = state.personaId,
                                onSelectPersona = onSelectPersona,
                            )
                        } else {
                            // One scroll position per chat: another chat (History's detail pane) opens at its end.
                            key(state.chatId) {
                                MessageList(
                                    messages = state.messages,
                                    streamingText = state.streamingText,
                                    canRetry = !state.isGenerating,
                                    onRetry = onRetry,
                                    branches = state.branches,
                                    onEdit = onEdit,
                                    onShowVersion = onShowVersion,
                                    speakingMessageId = state.speakingMessageId,
                                    onToggleSpeak = onToggleSpeak,
                                )
                            }
                        }
                    }
                    GenerationStatus(isGenerating = state.isGenerating)
                    ChatInput(
                        isGenerating = state.isGenerating,
                        onSend = onSend,
                        initialText = state.draft,
                        prompts = state.prompts,
                        voice = state.voice,
                        onMicClick = onMicClick,
                        photo = state.photo,
                        onPhotoPicked = onPhotoPicked,
                        onRemovePhoto = onRemovePhoto,
                        onStop = onStop,
                        modifier = Modifier.readableWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Tools on/off for this chat. A toggle button (its state is read out by TalkBack as on/off), so the
 * user can keep tool calls out of a chat, e.g. for creative writing where they only add delay.
 */
@Composable
private fun ToolsSwitch(enabled: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = enabled, onCheckedChange = { onToggle() }) {
        Icon(
            Icons.Outlined.Calculate,
            contentDescription = stringResource(Res.string.feature_chat_tools),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Maps a failure kind to a user-readable message. */
internal fun ErrorKind.messageRes(): StringResource = when (this) {
    ErrorKind.NO_MODEL -> Res.string.feature_chat_no_model_title
    ErrorKind.MODEL_LOAD_FAILED -> Res.string.feature_chat_model_failed
    ErrorKind.FILE_UNREADABLE -> Res.string.feature_chat_export_failed
    ErrorKind.IMAGES_NOT_SUPPORTED -> Res.string.feature_chat_images_not_supported
    else -> Res.string.feature_chat_generation_failed
}

/** User-readable text for each way the model can fail to load. */
private fun FailureReason.messageRes(): StringResource = when (this) {
    FailureReason.FILE_NOT_FOUND, FailureReason.FILE_DAMAGED -> Res.string.feature_chat_model_damaged
    FailureReason.INIT_FAILED -> Res.string.feature_chat_model_failed
    FailureReason.CRASHED_LAST_TIME -> Res.string.feature_chat_model_crashed
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
        isGenerating -> stringResource(Res.string.feature_chat_status_writing)
        showReady -> stringResource(Res.string.feature_chat_status_ready)
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = PmSpacing.l)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private const val READY_VISIBLE_MS = 3_000L

/**
 * The empty chat: the illustration, a promise ("nothing leaves this phone"), and four example
 * prompts. Suggestions answer the blank-page question "what can I ask?"; tapping one sends it.
 */
@Composable
private fun ChatWelcome(
    onSuggestion: (String) -> Unit,
    personas: List<Persona>,
    selectedPersonaId: String?,
    onSelectPersona: (String?) -> Unit,
) {
    val suggestions = listOf(
        stringResource(Res.string.feature_chat_suggestion_explain),
        stringResource(Res.string.feature_chat_suggestion_code),
        stringResource(Res.string.feature_chat_suggestion_ideas),
        stringResource(Res.string.feature_chat_suggestion_write),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(PmSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PmSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PmIllustration(Icons.AutoMirrored.Outlined.Chat)
        Text(
            stringResource(Res.string.feature_chat_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.feature_chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (personas.isNotEmpty()) {
            // "Answer as": the default assistant or a persona, for this new chat.
            Text(
                stringResource(Res.string.feature_chat_answer_as),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = PmSpacing.s),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PmSpacing.s, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(PmSpacing.s),
            ) {
                FilterChip(
                    selected = selectedPersonaId == null,
                    onClick = { onSelectPersona(null) },
                    label = { Text(stringResource(Res.string.feature_chat_persona_default)) },
                )
                personas.forEach { persona ->
                    FilterChip(
                        selected = persona.id == selectedPersonaId,
                        onClick = { onSelectPersona(persona.id) },
                        label = { Text(persona.name) },
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PmSpacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(PmSpacing.s),
            modifier = Modifier.padding(top = PmSpacing.s),
        ) {
            suggestions.forEach { text ->
                SuggestionChip(onClick = { onSuggestion(text) }, label = { Text(text) })
            }
        }
    }
}

@Composable
private fun ExportMenu(onExport: (ExportFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.feature_chat_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.feature_chat_export_markdown)) },
                onClick = {
                    open = false
                    onExport(ExportFormat.MARKDOWN)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.feature_chat_export_pdf)) },
                onClick = {
                    open = false
                    onExport(ExportFormat.PDF)
                },
            )
        }
    }
}

/** "Add two numbers in C.md": the chat title without characters file systems dislike. */
internal fun exportFileName(title: String?, format: ExportFormat): String {
    val base = title.orEmpty()
        .replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "")
        .trim()
        .take(MAX_FILE_NAME)
        .ifBlank { "PocketMind chat" }
    return "$base.${format.extension}"
}

private const val MAX_FILE_NAME = 60

private fun SpeechError.messageRes(): StringResource = when (this) {
    SpeechError.UNAVAILABLE -> Res.string.feature_chat_voice_unavailable
    SpeechError.PERMISSION -> Res.string.feature_chat_voice_permission
    SpeechError.NOTHING_HEARD -> Res.string.feature_chat_voice_nothing_heard
    SpeechError.FAILED -> Res.string.feature_chat_voice_failed
}
