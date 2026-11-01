package com.manjit.pocketmind.data.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.core.database.PocketMindDatabase
import com.manjit.pocketmind.core.llm.InferenceSettings
import com.manjit.pocketmind.core.llm.InferenceSettingsSource
import com.manjit.pocketmind.core.llm.SamplingParams
import com.manjit.pocketmind.core.llm.SessionReplacedException
import com.manjit.pocketmind.core.model.ChatTurn
import com.manjit.pocketmind.core.model.MessageStatus
import com.manjit.pocketmind.core.model.TurnRole
import com.manjit.pocketmind.core.testing.FakeLlmEngine
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
        engine.contextWindowTokens = 1200 // tiny window: ~176 tokens left for history
        engine.responsePieces = listOf("A long answer. " + "z".repeat(600))
        val chatId = repository.createChat()
        repeat(3) { sendAndFinish(chatId, "Question number $it?") }

        val last = engine.sessions.last()
        assertTrue(last.systemPrompt.startsWith("SYS"))
        assertTrue(last.systemPrompt.contains("Earlier in this conversation:"))
        assertTrue(last.history.size < 4)
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
