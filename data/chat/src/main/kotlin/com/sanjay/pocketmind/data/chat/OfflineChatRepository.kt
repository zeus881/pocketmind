package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.database.dao.ChatDao
import com.sanjay.pocketmind.core.database.dao.MessageDao
import com.sanjay.pocketmind.core.database.entity.asEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.GenerationStats
import com.sanjay.pocketmind.core.llm.HistoryTrimmer
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.TokenEstimator
import com.sanjay.pocketmind.core.model.Chat
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.core.model.ChatTurn
import com.sanjay.pocketmind.core.model.DeletedChat
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ChatRepository] backed by Room + the on-device [LlmEngine].
 *
 * The tricky part is keeping two "memories" consistent:
 *  - Room holds the chat as the user sees it (source of truth).
 *  - The engine's native conversation holds what the MODEL remembers.
 * They drift apart when the user switches chats, stops an answer halfway (the model saw a
 * partial answer), retries, changes settings, or when the model was unloaded for memory.
 * Before each request we check that, and if needed rebuild the conversation from Room —
 * trimmed to fit the context window.
 *
 * `@Singleton` because the session bookkeeping must be shared by every screen.
 */
@Singleton
internal class OfflineChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val engine: LlmEngine,
    private val settingsSource: InferenceSettingsSource,
    private val clock: Clock,
) : ChatRepository {

    /** What the engine's current conversation was built for. */
    private data class SessionKey(val chatId: String, val settings: InferenceSettings)

    private val sessionLock = Mutex()
    private var session: SessionKey? = null

    /** False after a stopped/failed answer: the model then remembers text that Room doesn't. */
    private var sessionInSync = false

    /** The engine's [LlmEngine.sessionId] right after WE built the session (-1 = never). */
    private var ownedSessionId = -1L
    private var lastTimestamp = 0L

    override val engineState: StateFlow<EngineState> = engine.state

    override fun observeChats(query: String): Flow<List<ChatSummary>> =
        chatDao.observeSummaries(query.trim()).map { rows ->
            rows.map { ChatSummary(Chat(it.id, it.title, it.createdAt, it.updatedAt), it.lastMessage) }
        }

    override fun observeChat(chatId: String): Flow<Chat?> = chatDao.observe(chatId).map { it?.asModel() }

    override fun observeMessages(chatId: String): Flow<List<Message>> =
        messageDao.observe(chatId).map { list -> list.map { it.asModel() } }

    override suspend fun ensureModelLoaded(): AppResult<Unit> = when (val loaded = loadSelectedModel()) {
        is AppResult.Failure -> loaded
        is AppResult.Success -> AppResult.Success(Unit)
    }

    /** Reads the current settings and loads that model. Returns the settings on success. */
    private suspend fun loadSelectedModel(): AppResult<InferenceSettings> {
        val settings = settingsSource.current() ?: return AppResult.Failure(ErrorKind.NO_MODEL)
        return when (val loaded = load(settings)) {
            is AppResult.Failure -> loaded
            is AppResult.Success -> AppResult.Success(settings)
        }
    }

    override fun resetModelCrashProtection() = engine.resetCrashProtection()

    override suspend fun createChat(): String {
        val now = nextTimestamp()
        val id = UUID.randomUUID().toString()
        chatDao.upsert(Chat(id, title = "", createdAt = now, updatedAt = now).asEntity())
        return id
    }

    override suspend fun sendMessage(chatId: String, text: String): AppResult<Flow<String>> {
        val settings = when (val loaded = loadSelectedModel()) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> loaded.value
        }
        val session = sessionLock.withLock {
            // History BEFORE the new message: the new message is sent as the prompt itself.
            val history = messageDao.getAll(chatId).map { it.asModel() }
            prepareSessionLocked(chatId, settings, history, prompt = text)

            val now = nextTimestamp()
            messageDao.upsert(Message(UUID.randomUUID().toString(), chatId, TurnRole.USER, text, now).asEntity())
            if (history.none { it.role == TurnRole.USER }) {
                chatDao.rename(chatId, ChatTitles.fromFirstMessage(text), now) // auto-title
            } else {
                chatDao.touch(chatId, now)
            }
            ownedSessionId
        }
        // The engine refuses to run this prompt if another feature replaced our session meanwhile.
        return AppResult.Success(engine.generate(text, session))
    }

    override suspend fun regenerateLastAnswer(chatId: String): AppResult<Flow<String>>? {
        val settings = when (val loaded = loadSelectedModel()) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> loaded.value
        }
        val prompt = sessionLock.withLock {
            val messages = messageDao.getAll(chatId).map { it.asModel() }
            val lastUserIndex = messages.indexOfLast { it.role == TurnRole.USER }
            if (lastUserIndex < 0) return null
            // Remove the old answer(s) after the last question.
            messages.drop(lastUserIndex + 1).forEach { messageDao.delete(it.id) }
            sessionInSync = false // the model still remembers the old answer
            prepareSessionLocked(chatId, settings, messages.take(lastUserIndex), messages[lastUserIndex].text)
            messages[lastUserIndex].text to ownedSessionId
        }
        return AppResult.Success(engine.generate(prompt.first, prompt.second))
    }

    override suspend fun saveAssistantMessage(
        chatId: String,
        text: String,
        status: MessageStatus,
        stats: GenerationStats?,
    ) {
        sessionLock.withLock {
            val now = nextTimestamp()
            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = TurnRole.ASSISTANT,
                text = text,
                createdAt = now,
                status = status,
                tokensPerSecond = stats?.decodeTokensPerSecond,
                backend = stats?.backend?.label,
            )
            messageDao.upsert(message.asEntity())
            chatDao.touch(chatId, now)
            sessionInSync = status == MessageStatus.COMPLETE
        }
    }

    override fun lastGenerationStats(): GenerationStats? = engine.lastStats

    override suspend fun renameChat(chatId: String, title: String) {
        chatDao.rename(chatId, title.trim().take(ChatTitles.MAX_LENGTH * 2), nextTimestamp())
    }

    override suspend fun deleteChat(chatId: String): DeletedChat? = sessionLock.withLock {
        val chat = chatDao.get(chatId) ?: return@withLock null
        val messages = messageDao.getAll(chatId).map { it.asModel() }
        chatDao.delete(chatId) // messages are removed by ON DELETE CASCADE
        if (session?.chatId == chatId) session = null
        DeletedChat(chat.asModel(), messages)
    }

    override suspend fun restoreChat(deleted: DeletedChat) {
        chatDao.restore(deleted.chat.asEntity(), deleted.messages.map { it.asEntity() })
    }

    /** Loads the model (a no-op if already loaded). A real reload changes [LlmEngine.sessionId]. */
    private suspend fun load(settings: InferenceSettings): AppResult<Unit> {
        engine.load(settings.modelPath, settings.preferGpu, settings.expectedSizeBytes)
        return when (engine.state.value) {
            is EngineState.Ready -> AppResult.Success(Unit)
            else -> AppResult.Failure(ErrorKind.MODEL_LOAD_FAILED)
        }
    }

    /**
     * Makes the engine's conversation match [history] for [chatId], rebuilding it only when
     * needed (rebuilding re-reads the whole history: "prefill" costs time on a phone).
     * Must be called while holding [sessionLock].
     */
    private suspend fun prepareSessionLocked(
        chatId: String,
        settings: InferenceSettings,
        history: List<Message>,
        prompt: String,
    ) {
        val turns = history
            .filter { it.status != MessageStatus.FAILED && it.text.isNotBlank() }
            .map { ChatTurn(it.role, it.text) }
        val budget = engine.contextWindowTokens - RESPONSE_RESERVE_TOKENS -
            TokenEstimator.estimate(settings.systemPrompt) - TokenEstimator.estimate(prompt)
        val historyTokens = turns.sumOf { TokenEstimator.estimate(it.text) }

        val key = SessionKey(chatId, settings)
        val upToDate = session == key &&
            sessionInSync &&
            // Someone else (benchmark, translate, a reload…) may have replaced the conversation.
            ownedSessionId == engine.sessionId &&
            historyTokens <= budget
        if (upToDate) return

        val trimmed = HistoryTrimmer.trim(turns, budget)
        val systemPrompt = listOfNotNull(settings.systemPrompt, trimmed.summaryNote).joinToString("\n\n")
        // Use the id RETURNED by newSession (reading engine.sessionId afterwards could already
        // see another feature's newer session).
        ownedSessionId = engine.newSession(systemPrompt, trimmed.turns, settings.sampling)
        session = key
        sessionInSync = true
    }

    /** Strictly increasing timestamps, so two messages saved in the same millisecond keep their order. */
    private fun nextTimestamp(): Long {
        lastTimestamp = maxOf(clock.nowMillis(), lastTimestamp + 1)
        return lastTimestamp
    }

    private companion object {
        /**
         * Tokens kept free for the model's answer. 1536 (not 1024) so a full program plus its
         * explanation fits; with less, long code answers were cut off near the end of a chat.
         */
        const val RESPONSE_RESERVE_TOKENS = 1536
    }
}
