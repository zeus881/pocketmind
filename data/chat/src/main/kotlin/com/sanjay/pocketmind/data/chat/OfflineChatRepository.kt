package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.database.dao.ChatDao
import com.sanjay.pocketmind.core.database.dao.MessageDao
import com.sanjay.pocketmind.core.database.entity.ConversationSummaryEntity
import com.sanjay.pocketmind.core.database.entity.asEntity
import com.sanjay.pocketmind.core.database.entity.asModel
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.GenerationStats
import com.sanjay.pocketmind.core.llm.HistoryTrimmer
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.TokenEstimator
import com.sanjay.pocketmind.core.model.BranchInfo
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
import kotlinx.coroutines.flow.combine
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
    private val summarizer: ConversationSummarizer,
    private val personas: PersonaRepository,
    private val images: ChatImageStore,
) : ChatRepository,
    ChatPhotos {

    /** What the engine's current conversation was built for. */
    private data class SessionKey(val chatId: String, val settings: InferenceSettings)

    private val sessionLock = Mutex()
    private var session: SessionKey? = null

    /** False after a stopped/failed answer: the model then remembers text that Room doesn't. */
    private var sessionInSync = false

    /** The engine's [LlmEngine.sessionId] right after WE built the session (-1 = never). */
    private var ownedSessionId = -1L

    /**
     * Estimated tokens the live conversation already holds (history + summary note). It grows by
     * each completed question and answer; when the next request would overflow, the session is
     * rebuilt with a trimmed history. Before v2 the rebuild happened on EVERY message once a chat
     * was long, re-reading ~2000 tokens each time.
     */
    private var sessionTokens = 0

    /** Tokens of the question being answered right now (added to [sessionTokens] on success). */
    private var pendingPromptTokens = 0
    private var lastTimestamp = 0L

    /** Photos attached in the chat input but not sent yet: not in Room, so they must not be cleaned up. */
    private val pendingImages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override val engineState: StateFlow<EngineState> = engine.state

    override fun observeChats(query: String): Flow<List<ChatSummary>> = SearchQuery.from(query).let { search ->
        chatDao.observeSummaries(search.raw, search.likePattern, search.ftsQuery).map { rows ->
            rows.map { row ->
                ChatSummary(
                    Chat(
                        row.id,
                        row.title,
                        row.createdAt,
                        row.updatedAt,
                        row.pinned,
                        row.folderId,
                        row.currentLeafId,
                    ),
                    row.lastMessage,
                )
            }
        }
    }

    override fun observeChat(chatId: String): Flow<Chat?> = chatDao.observe(chatId).map { it?.asModel() }

    override fun observeMessages(chatId: String): Flow<List<Message>> =
        combine(messageDao.observe(chatId), chatDao.observe(chatId)) { all, chat ->
            ConversationTree.path(all.map { it.asModel() }, chat?.currentLeafId)
        }

    override fun observeBranches(chatId: String): Flow<Map<String, BranchInfo>> =
        combine(messageDao.observe(chatId), chatDao.observe(chatId)) { all, chat ->
            val messages = all.map { it.asModel() }
            ConversationTree.branchInfo(messages, ConversationTree.path(messages, chat?.currentLeafId))
        }

    /** The visible conversation right now (current branch), oldest first. */
    private suspend fun currentPath(chatId: String): List<Message> =
        ConversationTree.path(messageDao.getAll(chatId).map { it.asModel() }, chatDao.get(chatId)?.currentLeafId)

    override suspend fun ensureModelLoaded(): AppResult<Unit> = when (val loaded = loadSelectedModel(vision = false)) {
        is AppResult.Failure -> loaded
        is AppResult.Success -> AppResult.Success(Unit)
    }

    /**
     * The settings for one chat: a chat started with a persona answers with that persona's
     * instructions (plus the honesty and safety rules every answer keeps) and, if the persona
     * has one, its own temperature. The model stays the same.
     */
    private suspend fun settingsFor(chatId: String, base: InferenceSettings): InferenceSettings {
        val persona = chatDao.get(chatId)?.personaId?.let { personas.persona(it) } ?: return base
        return base.copy(
            systemPrompt = "${persona.instructions}\n\n$PERSONA_RULES",
            sampling = persona.temperature?.let { base.sampling.copy(temperature = it.toDouble()) } ?: base.sampling,
        )
    }

    /**
     * Reads the current settings and loads that model, with its image encoder when [vision].
     * Returns the settings on success.
     */
    private suspend fun loadSelectedModel(vision: Boolean): AppResult<InferenceSettings> {
        val settings = settingsSource.current() ?: return AppResult.Failure(ErrorKind.NO_MODEL)
        if (vision && !settings.supportsImages) return AppResult.Failure(ErrorKind.IMAGES_NOT_SUPPORTED)
        return when (val loaded = load(settings, vision)) {
            is AppResult.Failure -> loaded
            is AppResult.Success -> AppResult.Success(settings)
        }
    }

    override fun resetModelCrashProtection() = engine.resetCrashProtection()

    override suspend fun imagesSupported(): Boolean = settingsSource.current()?.supportsImages == true

    override suspend fun attachImage(uri: String): String? = images.import(uri)?.also { pendingImages += it }

    override suspend fun discardImage(path: String) {
        pendingImages -= path
        images.delete(path)
    }

    override suspend fun createChat(personaId: String?): String {
        // A good moment to delete photos of chats deleted earlier (not at delete time: "Undo" still needs them).
        images.deleteAllExcept(messageDao.imagePaths().toSet() + pendingImages)
        val now = nextTimestamp()
        val id = UUID.randomUUID().toString()
        chatDao.upsert(Chat(id, title = "", createdAt = now, updatedAt = now, personaId = personaId).asEntity())
        return id
    }

    override suspend fun sendMessage(chatId: String, text: String, imagePath: String?): AppResult<Flow<String>> {
        val settings = when (val loaded = loadSelectedModel(vision = imagePath != null)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> settingsFor(chatId, loaded.value)
        }
        val session = sessionLock.withLock {
            // History BEFORE the new message: the new message is sent as the prompt itself.
            val history = currentPath(chatId)
            prepareSessionLocked(chatId, settings, history, prompt = text, withImage = imagePath != null)

            val now = nextTimestamp()
            val question = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = TurnRole.USER,
                text = text,
                createdAt = now,
                // v5 tree: the question follows the newest message of the chat's branch.
                parentId = history.lastOrNull()?.id,
                imagePath = imagePath,
            )
            messageDao.upsert(question.asEntity())
            imagePath?.let { pendingImages -= it } // saved in Room now
            chatDao.setCurrentLeaf(chatId, question.id)
            if (history.none { it.role == TurnRole.USER }) {
                chatDao.rename(chatId, ChatTitles.fromFirstMessage(text), now) // auto-title
            } else {
                chatDao.touch(chatId, now)
            }
            ownedSessionId
        }
        // The engine refuses to run this prompt if another feature replaced our session meanwhile.
        return AppResult.Success(engine.generate(text, session, imagePath))
    }

    override suspend fun regenerateLastAnswer(chatId: String): AppResult<Flow<String>>? {
        val photo = currentPath(chatId).lastOrNull { it.role == TurnRole.USER }?.imagePath
        val settings = when (val loaded = loadSelectedModel(vision = photo != null)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> settingsFor(chatId, loaded.value)
        }
        val prompt = sessionLock.withLock {
            val messages = currentPath(chatId)
            val lastUserIndex = messages.indexOfLast { it.role == TurnRole.USER }
            if (lastUserIndex < 0) return null
            // v2: the old answer is KEPT as another version. Showing the question as the newest
            // message makes the new answer its sibling ("‹ 2 / 2 ›" in the chat).
            chatDao.setCurrentLeaf(chatId, messages[lastUserIndex].id)
            sessionInSync = false // the model still remembers the old answer
            val question = messages[lastUserIndex]
            val earlier = messages.take(lastUserIndex)
            prepareSessionLocked(chatId, settings, earlier, question.text, withImage = question.imagePath != null)
            Triple(question.text, ownedSessionId, question.imagePath)
        }
        return AppResult.Success(engine.generate(prompt.first, prompt.second, prompt.third))
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
                parentId = chatDao.get(chatId)?.currentLeafId, // the question it answers
            )
            messageDao.upsert(message.asEntity())
            chatDao.setCurrentLeaf(chatId, message.id)
            chatDao.touch(chatId, now)
            sessionInSync = status == MessageStatus.COMPLETE
            if (sessionInSync) sessionTokens += pendingPromptTokens + TokenEstimator.estimate(text)
            pendingPromptTokens = 0
        }
    }

    override suspend fun editAndResend(chatId: String, messageId: String, newText: String): AppResult<Flow<String>> {
        // An edited question keeps its photo.
        val photo = currentPath(chatId).firstOrNull { it.id == messageId }?.imagePath
        val settings = when (val loaded = loadSelectedModel(vision = photo != null)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> settingsFor(chatId, loaded.value)
        }
        val session = sessionLock.withLock {
            val path = currentPath(chatId)
            val index = path.indexOfFirst { it.id == messageId && it.role == TurnRole.USER }
            if (index < 0) return AppResult.Failure(ErrorKind.UNKNOWN)
            // Everything from the edited question on is a different conversation now.
            chatDao.deleteSummary(chatId)
            sessionInSync = false
            prepareSessionLocked(chatId, settings, path.take(index), prompt = newText, withImage = photo != null)
            val now = nextTimestamp()
            val edited = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = TurnRole.USER,
                text = newText,
                createdAt = now,
                parentId = path[index].parentId, // a sibling of the original question
                imagePath = photo,
            )
            messageDao.upsert(edited.asEntity())
            chatDao.setCurrentLeaf(chatId, edited.id)
            chatDao.touch(chatId, now)
            ownedSessionId
        }
        return AppResult.Success(engine.generate(newText, session, photo))
    }

    override suspend fun showVersion(chatId: String, messageId: String, offset: Int) {
        sessionLock.withLock {
            val all = messageDao.getAll(chatId).map { it.asModel() }
            val message = all.firstOrNull { it.id == messageId } ?: return@withLock
            val versions = ConversationTree.siblings(all, message)
            val target = versions.getOrNull(versions.indexOf(message) + offset) ?: return@withLock
            chatDao.setCurrentLeaf(chatId, ConversationTree.newestLeafUnder(all, target.id))
            // The model remembers the branch it was on, and the cached summary may describe it.
            chatDao.deleteSummary(chatId)
            sessionInSync = false
        }
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) = chatDao.setPinned(chatId, pinned)

    override suspend fun moveToFolder(chatId: String, folderId: String?) = chatDao.setFolder(chatId, folderId)

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
    private suspend fun load(settings: InferenceSettings, vision: Boolean): AppResult<Unit> {
        engine.load(settings.modelPath, settings.preferGpu, settings.expectedSizeBytes, vision)
        return when (engine.state.value) {
            is EngineState.Ready -> AppResult.Success(Unit)
            else -> AppResult.Failure(ErrorKind.MODEL_LOAD_FAILED)
        }
    }

    /**
     * Makes the engine's conversation match [history] for [chatId], rebuilding it only when
     * needed (rebuilding re-reads the whole history: "prefill" costs time on a phone).
     * Must be called while holding [sessionLock].
     *
     * When the chat no longer fits, the newest turns that fill [HEADROOM_PERCENT] of the budget
     * are kept and everything older is replaced by a summary. The headroom means the next
     * several messages fit without another rebuild.
     */
    private suspend fun prepareSessionLocked(
        chatId: String,
        settings: InferenceSettings,
        history: List<Message>,
        prompt: String,
        withImage: Boolean = false,
    ) {
        val turns = history
            .filter { it.status != MessageStatus.FAILED && it.text.isNotBlank() }
            .map { ChatTurn(it.role, it.historyText()) }
        val promptTokens = TokenEstimator.estimate(prompt) + if (withImage) IMAGE_TOKEN_RESERVE else 0
        val budget = engine.contextWindowTokens - RESPONSE_RESERVE_TOKENS -
            TokenEstimator.estimate(settings.systemPrompt) - promptTokens
        pendingPromptTokens = promptTokens

        val key = SessionKey(chatId, settings)
        val upToDate = session == key &&
            sessionInSync &&
            // Someone else (benchmark, translate, a reload…) may have replaced the conversation.
            ownedSessionId == engine.sessionId &&
            sessionTokens <= budget
        if (upToDate) return

        val historyTokens = turns.sumOf { TokenEstimator.estimate(it.text) }
        val keptTurns: List<ChatTurn>
        val note: String?
        if (historyTokens <= budget) {
            keptTurns = turns
            note = null
        } else {
            val trimmed = HistoryTrimmer.trim(turns, budget * HEADROOM_PERCENT / PERCENT)
            keptTurns = trimmed.turns
            note = earlierContextNote(chatId, turns.take(turns.size - keptTurns.size), trimmed.summaryNote)
        }
        val systemPrompt = listOfNotNull(settings.systemPrompt, note).joinToString("\n\n")
        // Use the id RETURNED by newSession (reading engine.sessionId afterwards could already
        // see another feature's newer session — or our own summarizer's).
        ownedSessionId = engine.newSession(systemPrompt, keptTurns, settings.sampling)
        session = key
        sessionInSync = true
        sessionTokens = keptTurns.sumOf { TokenEstimator.estimate(it.text) } + TokenEstimator.estimate(note.orEmpty())
    }

    /**
     * A note about the [dropped] (oldest) turns for the system prompt: the cached model-written
     * summary, extended when more turns fell out of the window; or [fallback] (the extractive
     * first-sentence note) when no summary can be written.
     */
    private suspend fun earlierContextNote(chatId: String, dropped: List<ChatTurn>, fallback: String?): String? {
        if (dropped.isEmpty()) return fallback
        val cached = chatDao.summary(chatId)
        val summary = if (cached != null && cached.coveredTurns == dropped.size) {
            cached.summary
        } else {
            // Extend the cached summary with only the newly dropped turns; if the chat got shorter
            // (an answer was regenerated), start again from all dropped turns.
            val extendFrom = cached?.takeIf { it.coveredTurns < dropped.size }
            summarizer.summarize(
                previousSummary = extendFrom?.summary,
                turns = dropped.drop(extendFrom?.coveredTurns ?: 0),
            )?.also { written ->
                chatDao.upsertSummary(ConversationSummaryEntity(chatId, written, dropped.size, nextTimestamp()))
            }
        }
        return summary?.let { "$SUMMARY_HEADER\n$it" } ?: fallback
    }

    /** Strictly increasing timestamps, so two messages saved in the same millisecond keep their order. */
    private fun nextTimestamp(): Long {
        lastTimestamp = maxOf(clock.nowMillis(), lastTimestamp + 1)
        return lastTimestamp
    }

    internal companion object {
        /**
         * Tokens kept free for the model's answer. 1536 (not 1024) so a full program plus its
         * explanation fits; with less, long code answers were cut off near the end of a chat.
         */
        const val RESPONSE_RESERVE_TOKENS = 1536

        /** After a rebuild the kept history fills at most this share of the budget. */
        const val HEADROOM_PERCENT = 60
        const val PERCENT = 100

        const val SUMMARY_HEADER = "Summary of the earlier part of this conversation:"

        /**
         * Context budget kept for one photo. A BUDGET reserve, not a measurement: the real cost
         * depends on the model's image encoder and is to be measured on the device
         * (Conversation.getTokenCount before and after a photo).
         */
        const val IMAGE_TOKEN_RESERVE = 512

        internal const val PHOTO_NOTE = "[The user attached a photo to this message; it is no longer visible to you.]"

        /** Appended to every persona's instructions, including the user's own. */
        const val PERSONA_RULES = "You run fully offline on the user's phone. Answer the user's latest message. " +
            "If you are not sure about something, say so instead of guessing. Refuse requests that could cause harm."
    }
}

/**
 * A rebuilt conversation holds text only (re-reading every old photo would take far too long),
 * so a question that had a photo says so: the model then knows it can't see it any more.
 */
private fun Message.historyText(): String =
    if (imagePath == null) text else "${OfflineChatRepository.PHOTO_NOTE}\n$text"
