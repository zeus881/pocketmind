package com.sanjay.pocketmind.data.models

import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.llm.DefaultPrompts
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.model.LlmModelInfo
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.core.model.UserSettings
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The two "which model file do we use?" answers: chat ([SettingsInferenceSource]) and RAG embeddings. */
class ModelSourcesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val chat = info("chat", ModelKind.CHAT, size = 1_234)
    private val embed = info("embed", ModelKind.EMBEDDING, size = 99)
    private val settings = SettingsDataSource(InMemoryPreferencesDataStore())
    private lateinit var files: ModelFiles
    private lateinit var catalog: ModelCatalog

    private fun info(id: String, kind: ModelKind, size: Long) =
        LlmModelInfo(id, id.uppercase(), "", kind, "https://huggingface.co/$id", size, "0".repeat(64), 4, "MIT")

    @Before
    fun setUp() {
        files = ModelFiles.forTesting(tmp.newFolder())
        catalog = mockk {
            every { models } returns listOf(chat, embed)
            every { find(any()) } answers { listOf(chat, embed).firstOrNull { it.id == firstArg() } }
        }
    }

    private fun install(id: String) = files.finalFile(id).apply { parentFile?.mkdirs() }.writeText("x")

    @Test
    fun `no inference settings until a downloaded chat model is selected`() = runTest {
        val source = SettingsInferenceSource(settings, catalog, files)
        assertNull(source.current())

        settings.setSelectedModel(chat.id)
        assertNull(source.current()) // selected but not downloaded

        settings.setSelectedModel(embed.id)
        install(embed.id)
        assertNull(source.current()) // downloaded but not a chat model
    }

    @Test
    fun `inference settings carry the user's choices and the catalog size`() = runTest {
        install(chat.id)
        settings.setSelectedModel(chat.id)
        settings.setPreferGpu(false)
        settings.setTemperature(0.9f)
        settings.setTopK(20)

        val current = SettingsInferenceSource(settings, catalog, files).current()!!

        assertEquals(files.finalFile(chat.id).path, current.modelPath)
        assertEquals(false, current.preferGpu)
        assertEquals(DefaultPrompts.SYSTEM, current.systemPrompt)
        assertEquals(0.9, current.sampling.temperature, 0.0001)
        assertEquals(20, current.sampling.topK)
        assertEquals("CHAT", current.modelName)
        assertEquals(1_234L, current.expectedSizeBytes)
    }

    @Test
    fun `a custom system prompt replaces the default`() = runTest {
        install(chat.id)
        settings.setSelectedModel(chat.id)
        settings.setCustomSystemPrompt("Be brief.")

        assertEquals("Be brief.", SettingsInferenceSource(settings, catalog, files).current()!!.systemPrompt)
        assertEquals(
            UserSettings.DEFAULT_TOP_K,
            SettingsInferenceSource(settings, catalog, files).current()!!.sampling.topK,
        )
    }

    @Test
    fun `the embedding model path exists only after download`() = runTest {
        val source = CatalogEmbeddingModelSource(catalog, files)

        assertEquals("embed", source.embeddingModelId())
        assertNull(source.embeddingModelPath())

        install(embed.id)
        assertEquals(files.finalFile(embed.id).path, source.embeddingModelPath())
    }
}
