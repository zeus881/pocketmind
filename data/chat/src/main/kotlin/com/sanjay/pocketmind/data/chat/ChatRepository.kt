package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.GenerationStats
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Chat
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.core.model.DeletedChat
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single entry point for chat data, used by the chat and history screens.
 *
 * It combines two sources: Room (saved chats — the source of truth) and the [LlmEngine]
 * (live generation). ViewModels never touch DAOs or the engine directly; that keeps them
 * small and lets us change storage or runtime without touching UI code.
 */
interface ChatRepository {
    val engineState: StateFlow<EngineState>

    /** History list, filtered by [query] (title or message text), newest first. */
    fun observeChats(query: String): Flow<List<ChatSummary>>

    fun observeChat(chatId: String): Flow<Chat?>

    /** The VISIBLE conversation: the current branch of the chat's message tree, oldest first. */
    fun observeMessages(chatId: String): Flow<List<Message>>

    /** "Version i of n" for the visible messages that have alternatives (edited questions, regenerated answers). */
    fun observeBranches(chatId: String): Flow<Map<String, BranchInfo>>

    /** Lazily loads the selected model. Returns NO_MODEL / MODEL_LOAD_FAILED failures. */
    suspend fun ensureModelLoaded(): AppResult<Unit>

    /** Lets the next load retry backends that crashed the app before (the user chose "Try again"). */
    fun resetModelCrashProtection()

    /** Creates an empty chat and returns its id. */
    suspend fun createChat(): String

    /**
     * Saves the user's message and returns the stream of the assistant's answer.
     * Before generating it makes sure the model's conversation matches the saved history
     * (switching chats, after a stopped answer, or when the history must be trimmed).
     */
    suspend fun sendMessage(chatId: String, text: String): AppResult<Flow<String>>

    /**
     * Deletes the last assistant answer of the chat and generates it again for the last user
     * message. Returns null if there is no user message to answer.
     */
    suspend fun regenerateLastAnswer(chatId: String): AppResult<Flow<String>>?

    /**
     * Replaces the visible question [messageId] with [newText] as a NEW version (the old question
     * and everything after it stay available through the version switcher) and streams the answer.
     */
    suspend fun editAndResend(chatId: String, messageId: String, newText: String): AppResult<Flow<String>>

    /** Shows the previous ([offset] = -1) or next (+1) version of [messageId] and the conversation that followed it. */
    suspend fun showVersion(chatId: String, messageId: String, offset: Int)

    suspend fun setPinned(chatId: String, pinned: Boolean)

    /** Files the chat in [folderId], or takes it out of any folder (null). */
    suspend fun moveToFolder(chatId: String, folderId: String?)

    /** Writes the finished (or stopped/failed) answer to Room — once, not per token. */
    suspend fun saveAssistantMessage(chatId: String, text: String, status: MessageStatus, stats: GenerationStats?)

    /** Stats of the answer that just finished, if the engine measured any. */
    fun lastGenerationStats(): GenerationStats?

    suspend fun renameChat(chatId: String, title: String)

    /** Deletes the chat (messages cascade) and returns a snapshot for "Undo". */
    suspend fun deleteChat(chatId: String): DeletedChat?

    suspend fun restoreChat(deleted: DeletedChat)
}
