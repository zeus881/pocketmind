package com.sanjay.pocketmind.data.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.llm.SessionReplacedException
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.ChatTurn
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Repository tests: real in-memory Room + [FakeLlmEngine]. */
@RunWith(RobolectricTestRunner::class)
class OfflineChatRepositoryTest {
    private lateinit var db: PocketMindDatabase
    private lateinit var engine: FakeLlmEngine
    private lateinit var repository: OfflineChatRepository
    private var settings: InferenceSettings? = InferenceSettings("/model.litertlm", true, "SYS", SamplingParams())
    private var now = 1_000L

    /** Records summary requests and answers with a predictable text. */
    private val summaryCalls = mutableListOf<Pair<String?, Int>>()
    private var summaryResult: String? = "S"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
        engine = FakeLlmEngine()
        repository = OfflineChatRepository(
            chatDao = db.chatDao(),
            messageDao = db.messageDao(),
            engine = engine,
            settingsSource = object : InferenceSettingsSource {
                override suspend fun current() = settings
            },
            clock = { now },
            summarizer = { previous, turns ->
                summaryCalls += previous to turns.size
                summaryResult?.let { "$it${summaryCalls.size}" }
            },
            personas = RoomPersonaRepository(db.personaDao()) { now },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun sendAndFinish(chatId: String, text: String): String {
        val result = repository.sendMessage(chatId, text) as AppResult.Success
        val answer = result.value.toList().joinToString("")
        repository.saveAssistantMessage(chatId, answer, MessageStatus.COMPLETE, repository.lastGenerationStats())
        return answer
    }

    @Test
    fun firstMessageAutoTitlesTheChatAndSavesBothTurns() = runTest {
        val chatId = repository.createChat()

        val answer = sendAndFinish(chatId, "How do quadcopters stay level?")

        assertEquals("Hello from fake", answer)
        assertEquals("How do quadcopters stay level", repository.observeChat(chatId).first()!!.title)
        val messages = repository.observeMessages(chatId).first()
        assertEquals(listOf(TurnRole.USER, TurnRole.ASSISTANT), messages.map { it.role })
        assertEquals(12.4, messages[1].tokensPerSecond!!, 0.0)
        assertEquals("GPU", messages[1].backend)
    }

    @Test
    fun messagesFormAChainAndTheChatPointsAtTheNewest() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "one")
        sendAndFinish(chatId, "two")

        val messages = repository.observeMessages(chatId).first()
        assertEquals(listOf(null) + messages.dropLast(1).map { it.id }, messages.map { it.parentId })
        assertEquals(messages.last().id, repository.observeChat(chatId).first()?.currentLeafId)
    }

    @Test
    fun searchUsesTheFullTextIndexAndTreatsWildcardsLiterally() = runTest {
        val a = repository.createChat()
        sendAndFinish(a, "Battery life")
        sendAndFinish(a, "How long do quadcopters fly?") // only in a message, not in the title
        val b = repository.createChat()
        sendAndFinish(b, "Prices went up 50%")

        assertEquals(listOf(a), repository.observeChats("quadcop").first().map { it.chat.id })
        // "%" is a literal percent sign, not "anything": only the "50%" chat matches.
        assertEquals(listOf(b), repository.observeChats("%").first().map { it.chat.id })
    }

    @Test
    fun aChatStartedWithAPersonaAnswersWithItsInstructionsAndTemperature() = runTest {
        val chatId = repository.createChat(personaId = "builtin:coder")

        sendAndFinish(chatId, "Reverse a string in Kotlin")

        val session = engine.sessions.last()
        assertTrue(session.systemPrompt.startsWith("You are PocketMind in Coder mode"))
        assertTrue(session.systemPrompt.contains("Refuse requests that could cause harm"))
        assertEquals(0.2, session.sampling.temperature, 0.0001)
    }

    @Test
    fun aDeletedCustomPersonaFallsBackToTheDefaultPrompt() = runTest {
        val personas = RoomPersonaRepository(db.personaDao()) { now }
        val pirate = personas.savePersona(null, "Pirate", "Talk like a pirate.", null)!!
        val chatId = repository.createChat(personaId = pirate)
        personas.deletePersona(pirate)

        sendAndFinish(chatId, "hello")

        assertEquals("SYS", engine.sessions.last().systemPrompt)
    }

    @Test
    fun noModelSelectedReturnsNoModelFailure() = runTest {
        settings = null
        val chatId = repository.createChat()

        val result = repository.sendMessage(chatId, "hi")

        assertEquals(ErrorKind.NO_MODEL, (result as AppResult.Failure).kind)
    }

    @Test
    fun consecutiveMessagesReuseTheSameSession() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "one")
        sendAndFinish(chatId, "two")

        assertEquals(1, engine.sessions.size)
    }

    @Test
    fun switchingChatsRebuildsSessionWithThatChatsHistory() = runTest {
        val a = repository.createChat()
        sendAndFinish(a, "question A")
        val b = repository.createChat()
        sendAndFinish(b, "question B")

        sendAndFinish(a, "follow-up A")

        assertEquals(3, engine.sessions.size)
        assertEquals(
            listOf(ChatTurn(TurnRole.USER, "question A"), ChatTurn(TurnRole.ASSISTANT, "Hello from fake")),
            engine.sessions.last().history,
        )
    }

    @Test
    fun stoppedAnswerForcesARebuildOnTheNextMessage() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "one")
        repository.sendMessage(chatId, "two")
        repository.saveAssistantMessage(chatId, "Hel", MessageStatus.STOPPED, null)

        sendAndFinish(chatId, "three")

        assertEquals(2, engine.sessions.size)
    }

    @Test
    fun longHistoryIsTrimmedAndSummarizedIntoTheSystemPrompt() = runTest {
        engine.contextWindowTokens = 1712 // tiny window: 1536 reserved for the answer, ~176 left for history
        engine.responsePieces = listOf("A long answer. " + "z".repeat(600))
        val chatId = repository.createChat()
        repeat(3) { sendAndFinish(chatId, "Question number $it?") }

        val last = engine.sessions.last()
        assertTrue(last.systemPrompt.startsWith("SYS"))
        assertTrue(last.systemPrompt.contains("Summary of the earlier part of this conversation:\nS"))
        assertTrue(last.history.size < 4)
        assertNotNull(db.chatDao().summary(chatId)) // cached for the next messages
    }

    @Test
    fun aCachedSummaryIsReusedAndOnlyExtendedWithNewlyDroppedTurns() = runTest {
        engine.contextWindowTokens = 1712
        engine.responsePieces = listOf("A long answer. " + "z".repeat(600))
        val chatId = repository.createChat()
        repeat(5) { sendAndFinish(chatId, "Question number $it?") }

        // First summary from scratch; later ones continue the previous summary.
        assertNull(summaryCalls.first().first)
        assertTrue(summaryCalls.drop(1).all { it.first != null })
        val cached = db.chatDao().summary(chatId)!!
        assertEquals("S${summaryCalls.size}", cached.summary)
    }

    @Test
    fun withoutASummaryTheExtractiveNoteIsUsed() = runTest {
        summaryResult = null // e.g. generation failed
        engine.contextWindowTokens = 1712
        engine.responsePieces = listOf("A long answer. " + "z".repeat(600))
        val chatId = repository.createChat()
        repeat(3) { sendAndFinish(chatId, "Question number $it?") }

        assertTrue(engine.sessions.last().systemPrompt.contains("Earlier in this conversation:"))
        assertNull(db.chatDao().summary(chatId))
    }

    @Test
    fun aLongChatDoesNotRebuildTheSessionOnEveryMessage() = runTest {
        engine.contextWindowTokens = 3000 // ~1270 tokens of history budget
        engine.responsePieces = listOf("x".repeat(800)) // ~200 tokens per answer
        val chatId = repository.createChat()
        repeat(12) { sendAndFinish(chatId, "Question $it") }

        // v1 rebuilt on every message once the chat overflowed; with headroom it is far fewer.
        assertTrue("sessions: ${engine.sessions.size}", engine.sessions.size <= 5)
    }

    @Test
    fun sessionReplacedByAnotherFeatureIsRebuilt() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "one")
        // e.g. the benchmark or translate screen used the shared engine meanwhile:
        engine.newSession("Translate only.", emptyList(), SamplingParams())

        sendAndFinish(chatId, "two")

        assertEquals(3, engine.sessions.size)
        assertEquals("SYS", engine.sessions.last().systemPrompt)
        assertEquals(2, engine.sessions.last().history.size)
    }

    @Test
    fun answerRefusesToRunIfTheSessionWasReplacedAfterPreparing() = runTest {
        val chatId = repository.createChat()
        val answer = (repository.sendMessage(chatId, "hello") as AppResult.Success).value
        // Another feature grabs the engine between "prepare" and "collect".
        engine.newSession("Translate only.", emptyList(), SamplingParams())

        val failure = runCatching { answer.toList() }.exceptionOrNull()

        assertTrue(failure is SessionReplacedException)
        assertTrue(engine.prompts.isEmpty()) // the chat prompt never ran in the wrong session
    }

    @Test
    fun regenerateReplacesTheLastAnswer() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "question")
        engine.responsePieces = listOf("Better", " answer")

        val flow = (repository.regenerateLastAnswer(chatId) as AppResult.Success).value
        val answer = flow.toList().joinToString("")
        repository.saveAssistantMessage(chatId, answer, MessageStatus.COMPLETE, null)

        val messages = repository.observeMessages(chatId).first()
        assertEquals(listOf("question", "Better answer"), messages.map { it.text })
        assertEquals("question", engine.prompts.last())
    }

    @Test
    fun regenerateKeepsTheOldAnswerAsAnotherVersion() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "question")
        engine.responsePieces = listOf("Better")
        val again = (repository.regenerateLastAnswer(chatId) as AppResult.Success).value.toList().joinToString("")
        repository.saveAssistantMessage(chatId, again, MessageStatus.COMPLETE, null)

        val visible = repository.observeMessages(chatId).first()
        assertEquals(listOf("question", "Better"), visible.map { it.text })
        val answerVersions = repository.observeBranches(chatId).first()[visible.last().id]
        assertEquals(BranchInfo(index = 1, count = 2), answerVersions)

        repository.showVersion(chatId, visible.last().id, offset = -1)
        assertEquals(listOf("question", "Hello from fake"), repository.observeMessages(chatId).first().map { it.text })
    }

    @Test
    fun editAndResendCreatesANewBranchAndKeepsTheOldOne() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "first")
        sendAndFinish(chatId, "secnd question")
        val typo = repository.observeMessages(chatId).first()[2]

        val answer = (repository.editAndResend(chatId, typo.id, "second question") as AppResult.Success).value
        repository.saveAssistantMessage(chatId, answer.toList().joinToString(""), MessageStatus.COMPLETE, null)

        val visible = repository.observeMessages(chatId).first()
        assertEquals(listOf("first", "Hello from fake", "second question", "Hello from fake"), visible.map { it.text })
        assertEquals(BranchInfo(1, 2), repository.observeBranches(chatId).first()[visible[2].id])
        // The model was rebuilt with only the turns BEFORE the edited question.
        assertEquals(2, engine.sessions.last().history.size)

        repository.showVersion(chatId, visible[2].id, offset = -1)
        assertEquals("secnd question", repository.observeMessages(chatId).first()[2].text)
    }

    @Test
    fun editingAnAnswerIsRefused() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "q")
        val answer = repository.observeMessages(chatId).first().last()

        val result = repository.editAndResend(chatId, answer.id, "changed")

        assertEquals(ErrorKind.UNKNOWN, (result as AppResult.Failure).kind)
    }

    @Test
    fun pinsAndFoldersAreSaved() = runTest {
        val chatId = repository.createChat()
        repository.setPinned(chatId, true)
        repository.moveToFolder(chatId, "f1")

        val chat = repository.observeChat(chatId).first()!!
        assertTrue(chat.pinned)
        assertEquals("f1", chat.folderId)
    }

    @Test
    fun deleteThenRestoreBringsEverythingBack() = runTest {
        val chatId = repository.createChat()
        sendAndFinish(chatId, "keep me")

        val deleted = repository.deleteChat(chatId)
        assertNotNull(deleted)
        assertNull(repository.observeChat(chatId).first())

        repository.restoreChat(deleted!!)
        assertEquals(2, repository.observeMessages(chatId).first().size)
    }

    @Test
    fun searchFindsChatsByMessageText() = runTest {
        val a = repository.createChat()
        sendAndFinish(a, "Tell me about LiDAR")
        repository.createChat()

        val results = repository.observeChats("lidar").first()

        assertEquals(listOf(a), results.map { it.chat.id })
    }
}
