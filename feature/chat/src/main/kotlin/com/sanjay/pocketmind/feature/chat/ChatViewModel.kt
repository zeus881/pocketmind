package com.sanjay.pocketmind.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanjay.pocketmind.core.common.flow.throttleLatest
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.data.chat.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Logic of the chat screen.
 *
 * Data flow:
 * ```
 *  Room messages ─┐
 *  chat title ────┤
 *  streaming text ┼─ combine ─▶ StateFlow<ChatUiState> ─▶ ChatScreen
 *  model status ──┘
 * ```
 * The answer being generated lives ONLY in [streaming] (memory). It is written to Room once,
 * when it ends — finished, stopped, or failed. Writing every token to the database would
 * cost hundreds of disk writes per answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(private val repository: ChatRepository, savedStateHandle: SavedStateHandle) :
    ViewModel() {

    /** Which chat is shown. Null = a new chat that is created on the first message. */
    private val chatId = MutableStateFlow(savedStateHandle.get<String>(CHAT_ID_KEY))
    private val streaming = MutableStateFlow<String?>(null)
    private val noModel = MutableStateFlow(false)
    private val loadingModel = MutableStateFlow(false)
    private var generationJob: Job? = null

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)

    /** One-off events. A Channel delivers each event exactly once (a StateFlow would replay it). */
    val events: Flow<ChatEvent> = _events.receiveAsFlow()

    private val modelUi: Flow<ModelUi> = combine(
        repository.engineState,
        noModel,
        loadingModel,
    ) { engine, missing, loading ->
        when {
            missing -> ModelUi.NoModel
            engine is EngineState.Ready -> ModelUi.Ready(engine.backend.label)
            engine is EngineState.Loading || loading -> ModelUi.Loading
            engine is EngineState.Failed -> ModelUi.Failed(engine.reason)
            else -> ModelUi.Unloaded
        }
    }

    // flatMapLatest: when chatId changes, stop observing the old chat and observe the new one.

    /** The visible branch and its version info, together, so they always describe the same messages. */
    private val conversation: Flow<Pair<List<Message>, Map<String, BranchInfo>>> = chatId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList<Message>() to emptyMap())
        } else {
            combine(repository.observeMessages(id), repository.observeBranches(id)) { list, versions ->
                list to versions
            }
        }
    }
    private val title = chatId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeChat(id).map { it?.title }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        chatId,
        title,
        conversation,
        streaming,
        modelUi,
    ) { id, title, (list, versions), live, model ->
        ChatUiState(
            chatId = id,
            title = title,
            messages = list,
            branches = versions,
            streamingText = live,
            isGenerating = live != null,
            model = model,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState(chatId = chatId.value))

    init {
        // Lazy loading: the model is loaded when the chat screen opens, not at app start,
        // so the app itself starts instantly.
        loadModel()
    }

    fun loadModel() {
        viewModelScope.launch {
            loadingModel.value = true
            val result = repository.ensureModelLoaded()
            noModel.value = result is AppResult.Failure && result.kind == ErrorKind.NO_MODEL
            loadingModel.value = false
        }
    }

    /**
     * The "Try again" button of the model error state.
     *
     * After FailureReason.CRASHED_LAST_TIME the engine stopped retrying on its own to avoid a
     * crash loop. Pressing the button is the user asking for one more attempt on purpose, so the
     * crash records are cleared first. For every other reason it is a plain reload.
     */
    fun retryModel() {
        val model = uiState.value.model
        if (model is ModelUi.Failed && model.reason == FailureReason.CRASHED_LAST_TIME) {
            repository.resetModelCrashProtection()
        }
        loadModel()
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || generationJob?.isActive == true) return
        generationJob = viewModelScope.launch {
            val isNewChat = chatId.value == null
            val id = chatId.value ?: repository.createChat().also { chatId.value = it }
            streaming.value = "" // shows the "thinking" bubble immediately
            when (val result = guarded { repository.sendMessage(id, prompt) }) {
                is AppResult.Success -> collectAnswer(id, result.value)

                is AppResult.Failure -> {
                    discardIfEmpty(id, isNewChat)
                    fail(result.kind)
                }

                null -> discardIfEmpty(id, isNewChat) // the error was already reported by guarded()
            }
        }
    }

    /** Re-generates the last answer (after a stop, an error, or a bad answer). */
    fun retry() {
        val id = chatId.value ?: return
        if (generationJob?.isActive == true) return
        generationJob = viewModelScope.launch {
            streaming.value = ""
            when (val result = guarded { repository.regenerateLastAnswer(id) }) {
                is AppResult.Success -> collectAnswer(id, result.value)
                is AppResult.Failure -> fail(result.kind)
                null -> streaming.value = null // nothing to regenerate, or already reported
            }
        }
    }

    /**
     * Runs a repository call that may throw from native code (e.g. the model was unloaded for
     * memory between loading and starting the session). Turns the exception into an error event
     * instead of crashing the app; returns null in that case.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Chat request failed")
        fail(ErrorKind.GENERATION_FAILED)
        null
    }

    /**
     * A brand-new chat whose first message could not even be sent (no model, load failure) would
     * stay in History as an empty, untitled entry (AUDIT L-1). Remove it and start fresh.
     */
    private suspend fun discardIfEmpty(id: String, isNewChat: Boolean) {
        if (!isNewChat) return
        repository.deleteChat(id)
        if (chatId.value == id) chatId.value = null
    }

    /**
     * Sends an edited version of the question [messageId]. The original question and the
     * conversation after it stay available through the version switcher.
     */
    fun edit(messageId: String, newText: String) {
        val id = chatId.value ?: return
        val text = newText.trim()
        if (text.isEmpty() || generationJob?.isActive == true) return
        generationJob = viewModelScope.launch {
            streaming.value = ""
            when (val result = guarded { repository.editAndResend(id, messageId, text) }) {
                is AppResult.Success -> collectAnswer(id, result.value)
                is AppResult.Failure -> fail(result.kind)
                null -> Unit
            }
        }
    }

    /** Shows the previous (-1) or next (+1) version of a message. Not while an answer is being written. */
    fun showVersion(messageId: String, offset: Int) {
        val id = chatId.value ?: return
        if (generationJob?.isActive == true) return
        viewModelScope.launch { guarded { repository.showVersion(id, messageId, offset) } }
    }

    /** The Stop button. Cancellation travels down to the engine, which stops native generation. */
    fun stop() {
        generationJob?.cancel()
    }

    fun newChat() {
        stop()
        chatId.value = null
    }

    /**
     * Shows another saved chat in this same screen (History's detail pane on wide screens).
     * A running answer is stopped first; it is saved as "stopped" like any other interruption.
     */
    fun openChat(id: String?) {
        if (id == chatId.value) return
        stop()
        chatId.value = id
    }

    @Suppress("TooGenericExceptionCaught") // the engine's native layer can throw many exception types
    private suspend fun collectAnswer(chatId: String, answer: Flow<String>) {
        val text = StringBuilder()
        var status = MessageStatus.COMPLETE
        try {
            answer
                .onEach { piece -> text.append(piece) } // every piece is kept…
                .map { text.toString() }
                .throttleLatest(UI_THROTTLE_MS) // …but the UI redraws at most every 50 ms
                .collect { streaming.value = it }
        } catch (e: CancellationException) {
            status = MessageStatus.STOPPED // Stop button, new chat, or the screen was closed
            throw e
        } catch (e: Exception) { // any native/runtime failure from the engine
            Timber.e(e, "Generation failed")
            status = MessageStatus.FAILED
            _events.trySend(ChatEvent.Error(ErrorKind.GENERATION_FAILED))
        } finally {
            // NonCancellable: if the user left the screen, this coroutine is already cancelled,
            // but the partial answer must still be saved ("marked stopped", CLAUDE.md).
            withContext(NonCancellable) {
                val stats = if (status == MessageStatus.COMPLETE) repository.lastGenerationStats() else null
                repository.saveAssistantMessage(chatId, text.toString(), status, stats)
                streaming.value = null
            }
        }
    }

    private fun fail(kind: ErrorKind) {
        streaming.value = null
        if (kind == ErrorKind.NO_MODEL) noModel.value = true
        _events.trySend(ChatEvent.Error(kind))
    }

    companion object {
        /** Must equal the property name in ChatRoute: Navigation stores route args under it. */
        const val CHAT_ID_KEY = "chatId"
        private const val UI_THROTTLE_MS = 50L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
