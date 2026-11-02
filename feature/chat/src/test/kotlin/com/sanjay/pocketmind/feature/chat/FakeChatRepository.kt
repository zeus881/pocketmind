package com.sanjay.pocketmind.feature.chat

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.GenerationStats
import com.sanjay.pocketmind.core.llm.LlmBackend
import com.sanjay.pocketmind.core.model.Chat
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.core.model.DeletedChat
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.data.chat.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** In-memory [ChatRepository] for ViewModel tests. */
class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<Map<String, Chat>>(emptyMap())
    val messages = MutableStateFlow<List<Message>>(emptyList())
    override val engineState = MutableStateFlow<EngineState>(EngineState.Idle)

    var answerPieces = listOf("Hi", " there")
    var pieceDelayMs = 0L
    var answerError: Throwable? = null
    var modelAvailable = true
    private var nextId = 0

    override fun observeChats(query: String): Flow<List<ChatSummary>> = chats.map { all ->
        all.values.filter { it.title.contains(query, ignoreCase = true) }.map { ChatSummary(it, null) }
    }

    override fun observeChat(chatId: String): Flow<Chat?> = chats.map { it[chatId] }

    override fun observeMessages(chatId: String): Flow<List<Message>> =
        messages.map { all -> all.filter { it.chatId == chatId } }

    override suspend fun ensureModelLoaded(): AppResult<Unit> {
        if (!modelAvailable) return AppResult.Failure(ErrorKind.NO_MODEL)
        engineState.value = EngineState.Ready("/m", LlmBackend.GPU, 1)
        return AppResult.Success(Unit)
    }

    override suspend fun createChat(): String {
        val id = "chat${nextId++}"
        chats.value += id to Chat(id, "", 0, 0)
        return id
    }

    override suspend fun sendMessage(chatId: String, text: String): AppResult<Flow<String>> {
        if (!modelAvailable) return AppResult.Failure(ErrorKind.NO_MODEL)
        add(chatId, TurnRole.USER, text, MessageStatus.COMPLETE)
        chats.value += chatId to chats.value.getValue(chatId).copy(title = text)
        return AppResult.Success(answer())
    }

    override suspend fun regenerateLastAnswer(chatId: String): AppResult<Flow<String>>? {
        val last = messages.value.lastOrNull { it.chatId == chatId } ?: return null
        if (last.role == TurnRole.ASSISTANT) messages.value -= last
        return AppResult.Success(answer())
    }

    override suspend fun saveAssistantMessage(
        chatId: String,
        text: String,
        status: MessageStatus,
        stats: GenerationStats?,
    ) {
        add(chatId, TurnRole.ASSISTANT, text, status, stats?.decodeTokensPerSecond)
    }

    override fun lastGenerationStats() = GenerationStats(5, 12.4, 2, LlmBackend.GPU, true)

    override suspend fun renameChat(chatId: String, title: String) {
        chats.value += chatId to chats.value.getValue(chatId).copy(title = title)
    }

    override suspend fun deleteChat(chatId: String): DeletedChat? {
        val chat = chats.value[chatId] ?: return null
        val own = messages.value.filter { it.chatId == chatId }
        chats.value -= chatId
        messages.value -= own.toSet()
        return DeletedChat(chat, own)
    }

    override suspend fun restoreChat(deleted: DeletedChat) {
        chats.value += deleted.chat.id to deleted.chat
        messages.value += deleted.messages
    }

    private fun answer(): Flow<String> = flow {
        for (piece in answerPieces) {
            if (pieceDelayMs > 0) delay(pieceDelayMs)
            emit(piece)
        }
        answerError?.let { throw it }
    }

    private fun add(chatId: String, role: TurnRole, text: String, status: MessageStatus, tps: Double? = null) {
        messages.value += Message("m${nextId++}", chatId, role, text, nextId.toLong(), status, tps, null)
    }
}
