package com.manjit.pocketmind.feature.history

import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.llm.GenerationStats
import com.manjit.pocketmind.core.model.Chat
import com.manjit.pocketmind.core.model.ChatSummary
import com.manjit.pocketmind.core.model.DeletedChat
import com.manjit.pocketmind.core.model.Message
import com.manjit.pocketmind.core.model.MessageStatus
import com.manjit.pocketmind.core.testing.MainDispatcherRule
import com.manjit.pocketmind.data.chat.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    /** Minimal in-memory repository: only what the history screen uses. */
    private class FakeRepo : ChatRepository {
        val chats = MutableStateFlow(
            listOf(Chat("a", "Drone batteries", 0, 2), Chat("b", "Cooking rice", 0, 1)),
        )
        var queries = mutableListOf<String>()
        var failNext = false

        override val engineState = MutableStateFlow<EngineState>(EngineState.Idle)

        override fun observeChats(query: String): Flow<List<ChatSummary>> {
            queries += query
            if (failNext) {
                failNext = false
                return flow { error("db broken") }
            }
            return chats.map { list -> list.filter { it.title.contains(query, true) }.map { ChatSummary(it, null) } }
        }

        override suspend fun renameChat(chatId: String, title: String) {
            chats.value = chats.value.map { if (it.id == chatId) it.copy(title = title) else it }
        }

        override suspend fun deleteChat(chatId: String): DeletedChat? {
            val chat = chats.value.first { it.id == chatId }
            chats.value -= chat
            return DeletedChat(chat, emptyList())
        }

        override suspend fun restoreChat(deleted: DeletedChat) {
            chats.value += deleted.chat
        }

        override fun observeChat(chatId: String): Flow<Chat?> = emptyFlow()
        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun ensureModelLoaded(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun createChat() = "new"
        override suspend fun sendMessage(chatId: String, text: String): AppResult<Flow<String>> =
            AppResult.Success(emptyFlow())
        override suspend fun regenerateLastAnswer(chatId: String): AppResult<Flow<String>>? = null
        override suspend fun saveAssistantMessage(
            chatId: String,
            text: String,
            status: MessageStatus,
            stats: GenerationStats?,
        ) = Unit
        override fun lastGenerationStats(): GenerationStats? = null
    }

    private val repo = FakeRepo()
    private val viewModel by lazy { HistoryViewModel(repo) }

    private fun titles(): List<String> = (viewModel.uiState.value.list as ListState.Content).chats.map { it.chat.title }

    @Test
    fun `shows all chats initially`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("Drone batteries", "Cooking rice"), titles())
    }

    @Test
    fun `search is debounced and filters`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.onQueryChange("d")
        viewModel.onQueryChange("dr")
        viewModel.onQueryChange("drone")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("Drone batteries"), titles())
        // Intermediate keystrokes never reached the database.
        assertEquals(listOf("", "drone"), repo.queries)
    }

    @Test
    fun `delete emits undo event and undo restores`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.delete("a")
        advanceUntilIdle()
        assertEquals(listOf("Cooking rice"), titles())

        val event = viewModel.events.first() as HistoryEvent.ChatDeleted
        viewModel.undoDelete(event.deleted)
        advanceUntilIdle()
        assertEquals(setOf("Drone batteries", "Cooking rice"), titles().toSet())
    }

    @Test
    fun `rename changes the title and blank names are ignored`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.rename("b", "Biryani")
        viewModel.rename("a", "   ")
        advanceUntilIdle()
        assertEquals(listOf("Drone batteries", "Biryani"), titles())
    }

    @Test
    fun `error state then retry recovers`() = runTest(mainRule.testDispatcher) {
        repo.failNext = true
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(ListState.Error, viewModel.uiState.value.list)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, titles().size)
    }
}
