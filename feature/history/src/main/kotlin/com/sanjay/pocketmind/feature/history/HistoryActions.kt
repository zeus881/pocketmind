package com.sanjay.pocketmind.feature.history

/** Everything the History screen can ask for, grouped so composables don't take 11 lambdas. */
data class HistoryActions(
    val onQueryChange: (String) -> Unit,
    val onOpenChat: (chatId: String) -> Unit,
    val onRename: (chatId: String, title: String) -> Unit,
    val onDelete: (chatId: String) -> Unit,
    val onRetry: () -> Unit,
    val onSetPinned: (chatId: String, pinned: Boolean) -> Unit,
    val onMoveToFolder: (chatId: String, folderId: String?) -> Unit,
    val onSelectFolder: (folderId: String?) -> Unit,
    /** [moveChatId] = also file this chat in the new folder ("Move to → New folder"). */
    val onCreateFolder: (name: String, moveChatId: String?) -> Unit,
    val onRenameFolder: (folderId: String, name: String) -> Unit,
    val onDeleteFolder: (folderId: String) -> Unit,
) {
    companion object {
        /** For previews and screenshot tests. */
        val None =
            HistoryActions({}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {})
    }
}
