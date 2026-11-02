package com.sanjay.pocketmind.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    private fun viewModel(chatId: String? = null) =
        ChatViewModel(repository, SavedStateHandle(mapOf(ChatViewModel.CHAT_ID_KEY to chatId)))

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
