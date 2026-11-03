package com.sanjay.pocketmind.feature.history

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.GenerationStats
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Chat
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.core.model.DeletedChat
import com.sanjay.pocketmind.core.model.Folder
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.chat.ChatRepository
import com.sanjay.pocketmind.data.chat.FolderRepository
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
        override suspend fun createChat(personaId: String?, toolsEnabled: Boolean) = "new"

        override suspend fun setToolsEnabled(chatId: String, enabled: Boolean) = Unit
        override suspend fun sendMessage(chatId: String, text: String, imagePath: String?): AppResult<Flow<String>> =
            AppResult.Success(emptyFlow())

        override suspend fun regenerateLastAnswer(chatId: String): AppResult<Flow<String>>? = null
        override suspend fun saveAssistantMessage(
            chatId: String,
            text: String,
            status: MessageStatus,
            stats: GenerationStats?,
        ) = Unit
        override fun observeBranches(chatId: String): Flow<Map<String, BranchInfo>> = emptyFlow()
        override suspend fun editAndResend(chatId: String, messageId: String, newText: String) =
            AppResult.Failure(ErrorKind.UNKNOWN)
        override suspend fun showVersion(chatId: String, messageId: String, offset: Int) = Unit

        override suspend fun setPinned(chatId: String, pinned: Boolean) {
            chats.value = chats.value.map { if (it.id == chatId) it.copy(pinned = pinned) else it }
        }

        override suspend fun moveToFolder(chatId: String, folderId: String?) {
            chats.value = chats.value.map { if (it.id == chatId) it.copy(folderId = folderId) else it }
        }
    }

    private class FakeFolders : FolderRepository {
        val folders = MutableStateFlow<List<Folder>>(emptyList())

        override fun observeFolders(): Flow<List<Folder>> = folders
        override suspend fun createFolder(name: String): String? {
            val id = "f${folders.value.size + 1}"
            folders.value += Folder(id, name, 0)
            return id
        }
        override suspend fun renameFolder(id: String, name: String) {
            folders.value = folders.value.map { if (it.id == id) it.copy(name = name) else it }
        }
        override suspend fun deleteFolder(id: String) {
            folders.value = folders.value.filterNot { it.id == id }
        }
    }

    private val repo = FakeRepo()
    private val folderRepo = FakeFolders()
    private val viewModel by lazy { HistoryViewModel(repo, folderRepo) }

    @Test
    fun `selecting a folder lists only its chats, deleting it shows all again`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.createFolder("Drones", moveChatId = "a")
        advanceUntilIdle()
        val folderId = viewModel.uiState.value.folders.single().id

        viewModel.selectFolder(folderId)
        advanceUntilIdle()
        assertEquals(listOf("Drone batteries"), titles())

        viewModel.deleteFolder(folderId)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedFolderId)
        assertEquals(2, titles().size)
    }

    @Test
    fun `pin, move and folder rename are forwarded`() = runTest(mainRule.testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.setPinned("b", true)
        viewModel.moveToFolder("b", "x")
        viewModel.createFolder("Old", moveChatId = null)
        advanceUntilIdle()
        viewModel.renameFolder("f1", "New")
        advanceUntilIdle()

        val b = repo.chats.value.first { it.id == "b" }
        assertEquals(true, b.pinned)
        assertEquals("x", b.folderId)
        assertEquals(listOf("New"), viewModel.uiState.value.folders.map { it.name })
    }

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
