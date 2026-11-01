package com.manjit.pocketmind.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manjit.pocketmind.core.model.ChatSummary
import com.manjit.pocketmind.core.model.DeletedChat
import com.manjit.pocketmind.data.chat.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Screen state: the search text plus the result of loading the list. */
data class HistoryUiState(val query: String = "", val list: ListState = ListState.Loading)

sealed interface ListState {
    data object Loading : ListState

    data object Error : ListState

    data class Content(val chats: List<ChatSummary>) : ListState
}

sealed interface HistoryEvent {
    /** Shows "Chat deleted · Undo" with the snapshot needed to undo. */
    data class ChatDeleted(val deleted: DeletedChat) : HistoryEvent
}

/**
 * History list: live search, rename, delete with undo.
 *
 * Search is debounced: we wait until the user pauses typing for 250 ms before querying,
 * instead of running a database query for every keystroke.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(private val repository: ChatRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val retryTrigger = MutableStateFlow(0)
    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    // Debounce done by hand: mapLatest CANCELS the previous block when a new keystroke arrives,
    // so only a query that survives 250 ms without a newer one reaches the database.
    // An empty query (screen just opened, or cleared) is applied immediately.
    private val debouncedQuery: Flow<String> = query.mapLatest { q ->
        if (q.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
        q
    }

    private val list: Flow<ListState> = combine(debouncedQuery, retryTrigger) { q, _ -> q }
        .flatMapLatest { q ->
            repository.observeChats(q)
                .map<List<ChatSummary>, ListState> { ListState.Content(it) }
                .catch { emit(ListState.Error) }
        }

    val uiState: StateFlow<HistoryUiState> = combine(query, list, ::HistoryUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun retry() {
        retryTrigger.value++
    }

    fun rename(chatId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.renameChat(chatId, title) }
    }

    fun delete(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)?.let { _events.send(HistoryEvent.ChatDeleted(it)) }
        }
    }

    fun undoDelete(deleted: DeletedChat) {
        viewModelScope.launch { repository.restoreChat(deleted) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
