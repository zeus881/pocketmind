package com.sanjay.pocketmind.data.chat

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.database.UserDataStore
import com.sanjay.pocketmind.core.database.entity.NoteEntity
import com.sanjay.pocketmind.core.database.entity.PromptEntity
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.model.MessageStatus
import com.sanjay.pocketmind.core.model.ThemeMode
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Export all my data" writes everything into one ZIP; "Delete all my data" leaves nothing behind. */
@RunWith(RobolectricTestRunner::class)
class UserDataControlTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PocketMindDatabase
    private lateinit var repository: OfflineChatRepository
    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())
    private val images = FakeChatImageStore()
    private lateinit var control: AndroidUserDataControl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java).allowMainThreadQueries().build()
        repository = OfflineChatRepository(
            chatDao = db.chatDao(),
            messageDao = db.messageDao(),
            engine = FakeLlmEngine(),
            settingsSource = object : InferenceSettingsSource {
                override suspend fun current() = InferenceSettings("/m", true, "SYS", SamplingParams())
            },
            clock = { 1_000L },
            summarizer = { _, _ -> null },
            personas = RoomPersonaRepository(db.personaDao()) { 1_000L },
            images = images,
            tools = ChatTools(db.noteDao()) { 1_000L },
            answerEvents = AnswerEvents(),
        )
        val clock = { 1_762_158_900_000 }
        control = AndroidUserDataControl(context, UserDataStore(db), settings, images, clock, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun chatWithOneAnswer(): String {
        val chatId = repository.createChat()
        val answer = (repository.sendMessage(chatId, "How do drones fly?") as AppResult.Success).value.toList()
        repository.saveAssistantMessage(chatId, answer.joinToString(""), MessageStatus.COMPLETE, null)
        return chatId
    }

    private fun unzip(file: File): Map<String, String> = ZipInputStream(file.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().toString(Charsets.UTF_8) }
    }

    @Test
    fun `the export holds every chat as markdown and everything as json`() = runTest {
        chatWithOneAnswer()
        db.noteDao().insert(NoteEntity("n1", "Locker code is 4312", 5))
        db.personaDao().upsertPrompt(PromptEntity("p1", "Email", "Write a polite email", 5))
        settings.setThemeMode(ThemeMode.DARK)
        val target = File(context.cacheDir, "export.zip").apply { delete() }

        val result = control.exportAll(Uri.fromFile(target))

        assertTrue(result is AppResult.Success)
        val files = unzip(target)
        val expected = listOf("README.txt", "chats/001-How do drones fly.md")
        assertTrue(files.keys.toString(), files.keys.containsAll(expected))
        assertTrue(files.getValue("chats/001-How do drones fly.md").contains("Hello from fake"))
        val json = JSONObject(files.getValue(UserDataZip.JSON_NAME))
        assertEquals("2025-11-03T08:35:00Z", json.getString("exportedAt"))
        val messages = json.getJSONArray("chats").getJSONObject(0).getJSONArray("messages")
        val roles = (0 until messages.length()).map { messages.getJSONObject(it).getString("role") }
        assertEquals(listOf("USER", "ASSISTANT"), roles)
        assertEquals("Locker code is 4312", json.getJSONArray("notes").getJSONObject(0).getString("text"))
        assertEquals("Email", json.getJSONArray("prompts").getJSONObject(0).getString("title"))
        assertEquals("DARK", json.getJSONObject("settings").getString("theme"))
    }

    @Test
    fun `chat titles become safe, numbered file names`() {
        assertEquals("001-a b c.md", UserDataZip.chatFileName(0, "a/b:c"))
        assertEquals("012-Chat.md", UserDataZip.chatFileName(11, "  "))
    }

    @Test
    fun `delete all empties the database, photos and settings but keeps the model`() = runTest {
        chatWithOneAnswer()
        db.noteDao().insert(NoteEntity("n1", "secret", 5))
        images.import("content://cat")
        settings.setSelectedModel("gemma-4-e2b")
        settings.setThemeMode(ThemeMode.DARK)

        control.deleteAll()

        assertTrue(repository.observeChats("").first().isEmpty())
        assertTrue(db.noteDao().recent(10).isEmpty())
        assertTrue(images.stored.isEmpty())
        val after = settings.current()
        assertEquals(ThemeMode.SYSTEM, after.themeMode)
        assertEquals("gemma-4-e2b", after.selectedModelId)
        assertTrue(after.onboardingDone)
    }
}
