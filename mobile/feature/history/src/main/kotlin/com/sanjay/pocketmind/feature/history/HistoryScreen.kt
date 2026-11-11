package com.sanjay.pocketmind.feature.history

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.designsystem.component.EmptyState
import com.sanjay.pocketmind.core.designsystem.component.ErrorState
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.core.model.Folder
import org.koin.compose.viewmodel.koinViewModel

/** Stateful history screen: wires the ViewModel to [HistoryContent] and shows the Undo snackbar. */
@Composable
fun HistoryScreen(
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.ChatDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.feature_history_deleted),
                        actionLabel = resources.getString(R.string.feature_history_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.deleted)
                }
            }
        }
    }

    HistoryContent(
        state = state,
        actions = HistoryActions(
            onQueryChange = viewModel::onQueryChange,
            onOpenChat = onOpenChat,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onRetry = viewModel::retry,
            onSetPinned = viewModel::setPinned,
            onMoveToFolder = viewModel::moveToFolder,
            onSelectFolder = viewModel::selectFolder,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
        ),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** What a dialog on this screen is open for (one at a time). */
private sealed interface HistoryDialog {
    data class RenameChat(val summary: ChatSummary) : HistoryDialog

    data class MoveChat(val summary: ChatSummary) : HistoryDialog

    /** [moveChatId]: the chat to file in the new folder, when opened from "Move to". */
    data class NewFolder(val moveChatId: String?) : HistoryDialog

    data class RenameFolder(val folder: Folder) : HistoryDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by remember { mutableStateOf<HistoryDialog?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_history_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Centred at a readable width on tablets and foldables.
                .readableWidth(),
        ) {
            SearchField(state.query, actions.onQueryChange)
            FolderBar(
                folders = state.folders,
                selectedId = state.selectedFolderId,
                onSelect = actions.onSelectFolder,
                onNew = { dialog = HistoryDialog.NewFolder(moveChatId = null) },
                onRename = { dialog = HistoryDialog.RenameFolder(it) },
                onDelete = actions.onDeleteFolder,
            )
            Box(Modifier.weight(1f)) {
                when (val list = state.list) {
                    ListState.Loading -> LoadingState()

                    ListState.Error -> ErrorState(stringResource(R.string.feature_history_error), actions.onRetry)

                    is ListState.Content -> when {
                        list.chats.isEmpty() && state.query.isBlank() -> EmptyState(
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.feature_history_empty_title),
                            body = stringResource(R.string.feature_history_empty_body),
                        )

                        list.chats.isEmpty() -> EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.feature_history_no_results, state.query),
                        )

                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(list.chats, key = { it.chat.id }) { summary ->
                                ChatRow(
                                    summary = summary,
                                    query = state.query,
                                    actions = actions,
                                    onRename = { dialog = HistoryDialog.RenameChat(summary) },
                                    onMove = { dialog = HistoryDialog.MoveChat(summary) },
                                    modifier = Modifier.animateItem(),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit

        is HistoryDialog.RenameChat -> NameDialog(
            title = stringResource(R.string.feature_history_rename),
            initial = open.summary.chat.title,
            onConfirm = { actions.onRename(open.summary.chat.id, it) },
            onDismiss = { dialog = null },
        )

        is HistoryDialog.MoveChat -> MoveDialog(
            summary = open.summary,
            folders = state.folders,
            onMove = { folderId ->
                actions.onMoveToFolder(open.summary.chat.id, folderId)
                dialog = null
            },
            onNewFolder = { dialog = HistoryDialog.NewFolder(moveChatId = open.summary.chat.id) },
            onDismiss = { dialog = null },
        )

        is HistoryDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.feature_history_new_folder),
            initial = "",
            onConfirm = { actions.onCreateFolder(it, open.moveChatId) },
            onDismiss = { dialog = null },
        )

        is HistoryDialog.RenameFolder -> NameDialog(
            title = stringResource(R.string.feature_history_rename_folder),
            initial = open.folder.name,
            onConfirm = { actions.onRenameFolder(open.folder.id, it) },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.feature_history_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.feature_history_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PmSpacing.l, vertical = PmSpacing.s),
    )
}

/**
 * "All chats" + one chip per folder + "New folder". With a folder selected, a menu offers
 * renaming or deleting it (its chats are kept).
 */
@Composable
private fun FolderBar(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (String) -> Unit,
) {
    val selected = folders.firstOrNull { it.id == selectedId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PmSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(PmSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.feature_history_all_chats)) },
        )
        folders.forEach { folder ->
            FilterChip(selected = folder.id == selectedId, onClick = {
                onSelect(folder.id)
            }, label = { Text(folder.name) })
        }
        AssistChip(
            onClick = onNew,
            label = { Text(stringResource(R.string.feature_history_new_folder)) },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        )
        if (selected != null) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.feature_history_folder_options, selected.name),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_history_rename_folder)) },
                        onClick = {
                            menuOpen = false
                            onRename(selected)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_history_delete_folder)) },
                        onClick = {
                            menuOpen = false
                            onDelete(selected.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    summary: ChatSummary,
    query: String,
    actions: HistoryActions,
    onRename: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val chat = summary.chat
    val title = chat.title.ifBlank { stringResource(R.string.feature_history_untitled) }
    ListItem(
        modifier = modifier.clickable(onClick = { actions.onOpenChat(chat.id) }),
        leadingContent = if (chat.pinned) {
            { Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.feature_history_pinned)) }
        } else {
            null
        },
        headlineContent = { Text(highlighted(title, query), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = summary.lastMessage?.let { last ->
            { Text(highlighted(last, query), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        overlineContent = { Text(DateUtils.getRelativeTimeSpanString(chat.updatedAt).toString()) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.feature_history_more, title),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(if (chat.pinned) R.string.feature_history_unpin else R.string.feature_history_pin) {
                        menuOpen = false
                        actions.onSetPinned(chat.id, !chat.pinned)
                    }
                    MenuItem(R.string.feature_history_move_to_folder) {
                        menuOpen = false
                        onMove()
                    }
                    MenuItem(R.string.feature_history_rename) {
                        menuOpen = false
                        onRename()
                    }
                    MenuItem(R.string.feature_history_delete) {
                        menuOpen = false
                        actions.onDelete(chat.id)
                    }
                }
            }
        },
    )
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/** Bold + tinted search words (by word start, like the search itself). */
@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    return remember(text, query, style) {
        buildAnnotatedString {
            append(text)
            searchMatches(text, query).forEach { addStyle(style, it.first, it.last + 1) }
        }
    }
}

/** "Move to": no folder, each folder, or a new one. */
@Composable
private fun MoveDialog(
    summary: ChatSummary,
    folders: List<Folder>,
    onMove: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.feature_history_no_folder)) +
        folders.map { it.id to it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_history_move_to_folder)) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (id, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { RadioButton(selected = summary.chat.folderId == id, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = summary.chat.folderId == id,
                            role = Role.RadioButton,
                            onClick = { onMove(id) },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNewFolder) { Text(stringResource(R.string.feature_history_new_folder)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_history_cancel)) } },
    )
}

/** One text field, used for renaming chats and folders and naming new folders. */
@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.feature_history_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_history_cancel)) } },
    )
}
