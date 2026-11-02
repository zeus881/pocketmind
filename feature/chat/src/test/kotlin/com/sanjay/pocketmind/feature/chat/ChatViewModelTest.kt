package com.sanjay.pocketmind.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.chat.ChatExporter
import com.sanjay.pocketmind.data.chat.PersonaRepository
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeChatRepository()

    private val exporter = FakeExporter()

    /** android.net.Uri has no real implementation in JVM tests; the fake exporter never reads it. */
    private val anyUri: Uri = mockk()

    private val personas = object : PersonaRepository {
        val list = listOf(Persona("builtin:coder", "Coder", "code", 0.2f, builtIn = true))
        override fun observePersonas(): Flow<List<Persona>> = flowOf(list)
        override suspend fun persona(id: String) = list.firstOrNull { it.id == id }
        override suspend fun savePersona(id: String?, name: String, instructions: String, temperature: Float?) = null
        override suspend fun deletePersona(id: String) = Unit
        override fun observePrompts(): Flow<List<SavedPrompt>> = flowOf(listOf(SavedPrompt("p", "Email", "Write")))
        override suspend fun savePrompt(id: String?, title: String, text: String) = null
        override suspend fun deletePrompt(id: String) = Unit
    }

    private fun viewModel(chatId: String? = null, draft: String? = null) = ChatViewModel(
        repository,
        exporter,
        personas,
        SavedStateHandle(mapOf(ChatViewModel.CHAT_ID_KEY to chatId, ChatViewModel.DRAFT_KEY to draft)),
    )

    @Test
    fun `a persona picked on the welcome screen is used for the new chat`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("Coder"), vm.uiState.value.personas.map { it.name })
        assertEquals(listOf("Email"), vm.uiState.value.prompts.map { it.title })

        vm.selectPersona("builtin:coder")
        advanceUntilIdle()
        assertEquals("Coder", vm.uiState.value.personaName)
        vm.send("Sort a list")
        advanceUntilIdle()

        assertEquals("builtin:coder", repository.chats.value.values.single().personaId)
        // Once the chat exists, its persona can't be switched from the welcome chips.
        vm.selectPersona(null)
        advanceUntilIdle()
        assertEquals("builtin:coder", vm.uiState.value.personaId)
    }

    private class FakeExporter : ChatExporter {
        val written = mutableListOf<Pair<String, Int>>()
        var result: AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun writeMarkdown(uri: Uri, title: String, messages: List<Message>): AppResult<Unit> {
            written += "md:$title" to messages.size
            return result
        }

        override suspend fun writePdf(uri: Uri, title: String, messages: List<Message>): AppResult<Unit> {
            written += "pdf:$title" to messages.size
            return result
        }
    }

    @Test
    fun `shared text arrives as a draft, not as a sent message`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel(draft = "Summarize this: …")
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("Summarize this: …", vm.uiState.value.draft)
        assertEquals(emptyList<Any>(), repository.messages.value)
    }

    @Test
    fun `export writes the visible conversation and reports the result`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("Hello")
        advanceUntilIdle()

        vm.export(anyUri, ExportFormat.PDF)
        advanceUntilIdle()
        assertEquals(listOf("pdf:Hello" to 2), exporter.written)
        assertEquals(ChatEvent.Exported, vm.events.first())

        exporter.result = AppResult.Failure(ErrorKind.FILE_UNREADABLE)
        vm.export(anyUri, ExportFormat.MARKDOWN)
        advanceUntilIdle()
        assertEquals(ChatEvent.Error(ErrorKind.FILE_UNREADABLE), vm.events.first())
    }

    @Test
    fun `export file names drop characters file systems dislike`() {
        assertEquals("Add two numbers in C.md", exportFileName("Add two numbers in C?", ExportFormat.MARKDOWN))
        assertEquals("PocketMind chat.pdf", exportFileName("  ///  ", ExportFormat.PDF))
    }

    @Test
    fun `opening the screen lazily loads the model`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ModelUi.Ready("GPU"), vm.uiState.value.model)
    }

    @Test
    fun `no model shows the no-model state`() = runTest(mainRule.testDispatcher) {
        repository.modelAvailable = false
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ModelUi.NoModel, vm.uiState.value.model)
    }

    @Test
    fun `a load failure shows its reason`() = runTest(mainRule.testDispatcher) {
        repository.loadFailure = FailureReason.FILE_DAMAGED
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ModelUi.Failed(FailureReason.FILE_DAMAGED), vm.uiState.value.model)
    }

    @Test
    fun `try again after a crash clears the crash records and loads`() = runTest(mainRule.testDispatcher) {
        repository.loadFailure = FailureReason.CRASHED_LAST_TIME
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ModelUi.Failed(FailureReason.CRASHED_LAST_TIME), vm.uiState.value.model)

        vm.retryModel()
        advanceUntilIdle()

        assertEquals(1, repository.crashResets)
        assertEquals(ModelUi.Ready("GPU"), vm.uiState.value.model)
    }

    @Test
    fun `plain retry does not clear crash records`() = runTest(mainRule.testDispatcher) {
        repository.loadFailure = FailureReason.INIT_FAILED
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.retryModel()
        advanceUntilIdle()

        assertEquals(0, repository.crashResets)
        assertEquals(2, repository.loadAttempts)
    }

    @Test
    fun `send creates a chat and saves the complete answer once`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("  Hello  ")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("chat0", state.chatId)
        assertEquals(listOf("Hello", "Hi there"), state.messages.map { it.text })
        assertEquals(MessageStatus.COMPLETE, state.messages.last().status)
        assertEquals(12.4, state.messages.last().tokensPerSecond!!, 0.0)
        assertNull(state.streamingText)
        assertFalse(state.isGenerating)
    }

    @Test
    fun `a first message that can't be sent leaves no empty chat behind`() = runTest(mainRule.testDispatcher) {
        repository.modelAvailable = false
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.send("Hello")
        advanceUntilIdle()

        assertEquals(emptyMap<String, Any>(), repository.chats.value)
        assertNull(vm.uiState.value.chatId)
    }

    @Test
    fun `a failed send in an existing chat keeps the chat`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("first")
        advanceUntilIdle()

        repository.modelAvailable = false
        vm.send("second")
        advanceUntilIdle()

        assertEquals(setOf("chat0"), repository.chats.value.keys)
    }

    @Test
    fun `openChat switches the shown chat in place`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("first chat")
        advanceUntilIdle()
        val first = vm.uiState.value.chatId
        vm.newChat()
        vm.send("second chat")
        advanceUntilIdle()

        vm.openChat(first)
        advanceUntilIdle()

        assertEquals(first, vm.uiState.value.chatId)
        assertEquals(listOf("first chat", "Hi there"), vm.uiState.value.messages.map { it.text })
    }

    @Test
    fun `edit sends a new version and streams its answer`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("secnd")
        advanceUntilIdle()
        val question = vm.uiState.value.messages.first()

        vm.edit(question.id, "  second  ")
        advanceUntilIdle()

        assertEquals(listOf(question.id to "second"), repository.edits)
        assertEquals("Hi there", vm.uiState.value.messages.last().text)
    }

    @Test
    fun `version info reaches the state and switching is forwarded`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel("chat0").also { repository.createChat() }
        backgroundScope.launch { vm.uiState.collect {} }
        repository.branches.value = mapOf("m1" to BranchInfo(0, 2))
        advanceUntilIdle()
        assertEquals(BranchInfo(0, 2), vm.uiState.value.branches["m1"])

        vm.showVersion("m1", 1)
        advanceUntilIdle()

        assertEquals(listOf("m1" to 1), repository.versionRequests)
    }

    @Test
    fun `blank input is ignored`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        vm.send("   ")
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), repository.messages.value)
    }

    @Test
    fun `stop saves the partial answer marked stopped`() = runTest(mainRule.testDispatcher) {
        repository.pieceDelayMs = 100
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("Hello")
        advanceTimeBy(150) // "Hi" arrived at 100 ms, " there" would arrive at 200 ms
        vm.stop()
        advanceUntilIdle()

        val answer = repository.messages.value.last()
        assertEquals(TurnRole.ASSISTANT, answer.role)
        assertEquals("Hi", answer.text)
        assertEquals(MessageStatus.STOPPED, answer.status)
    }

    @Test
    fun `generation error saves a failed message and emits an error event`() = runTest(mainRule.testDispatcher) {
        repository.answerError = IllegalStateException("boom")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("Hello")
        advanceUntilIdle()

        assertEquals(MessageStatus.FAILED, repository.messages.value.last().status)
        assertEquals(ChatEvent.Error(ErrorKind.GENERATION_FAILED), vm.events.first())
    }

    @Test
    fun `retry replaces the last answer`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("Hello")
        advanceUntilIdle()
        repository.answerPieces = listOf("Second")

        vm.retry()
        advanceUntilIdle()

        assertEquals(listOf("Hello", "Second"), vm.uiState.value.messages.map { it.text })
    }

    @Test
    fun `new chat clears the screen`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("Hello")
        advanceUntilIdle()

        vm.newChat()
        advanceUntilIdle()

        assertNull(vm.uiState.value.chatId)
        assertEquals(emptyList<Any>(), vm.uiState.value.messages)
    }

    @Test
    fun `reopening a saved chat shows its messages`() = runTest(mainRule.testDispatcher) {
        val first = viewModel()
        backgroundScope.launch { first.uiState.collect {} }
        first.send("Hello")
        advanceUntilIdle()

        val reopened = viewModel(chatId = "chat0")
        backgroundScope.launch { reopened.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2, reopened.uiState.value.messages.size)
    }
}
