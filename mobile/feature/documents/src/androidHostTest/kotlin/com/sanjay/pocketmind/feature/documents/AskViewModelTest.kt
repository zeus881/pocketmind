package com.sanjay.pocketmind.feature.documents

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.database.PocketMindDatabase
import com.sanjay.pocketmind.core.llm.EmbeddingModelSource
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.FakeTextEmbedder
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.documents.ExtractedDocument
import com.sanjay.pocketmind.data.documents.PageText
import com.sanjay.pocketmind.data.documents.PdfTextExtractor
import com.sanjay.pocketmind.data.documents.rag.DocumentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AskViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var db: PocketMindDatabase
    private val engine = FakeLlmEngine(responsePieces = listOf("Thirty minutes ", "[p. 2]."))
    private var embeddingPath: String? = "/embed"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private val viewModel by lazy {
        val repository = DocumentRepository(
            extractor = object : PdfTextExtractor {
                override suspend fun extract(uri: String) = AppResult.Success(
                    ExtractedDocument(
                        "drone.pdf",
                        listOf(
                            PageText(1, "The frame is carbon fibre."),
                            PageText(2, "The battery gives a flight time of thirty minutes."),
                        ),
                    ),
                )
            },
            embedder = FakeTextEmbedder(),
            embeddingModel = object : EmbeddingModelSource {
                override suspend fun embeddingModelPath() = embeddingPath
                override fun embeddingModelId() = "embed"
            },
            task = LlmTask(
                engine,
                object : InferenceSettingsSource {
                    override suspend fun current() = InferenceSettings("/m", true, "S", SamplingParams())
                },
            ),
            dao = db.documentDao(),
            clock = { 0L },
            cpu = UnconfinedTestDispatcher(mainRule.testDispatcher.scheduler),
        )
        AskViewModel(repository)
    }

    @Test
    fun addingAPdfSelectsItAndAskingGivesACitedAnswerWithSources() = runTest {
        viewModel.addPdf("content://docs/drone.pdf")
        viewModel.uiState.first { it.selectedId != null && it.indexing == null }

        viewModel.onQuestionChange("How long does it fly?")
        viewModel.ask()
        val state = viewModel.uiState.first { !it.isAnswering && it.answer?.isNotEmpty() == true }

        assertEquals("Thirty minutes [p. 2].", state.answer)
        assertEquals(setOf(2), state.citedPages)
        assertEquals(2, state.sources.size)
    }

    @Test
    fun missingEmbeddingModelIsShown() = runTest {
        embeddingPath = null
        val state = viewModel.uiState.first { !it.isLoading }
        assertFalse(state.embeddingModelReady)
    }

    @Test
    fun indexingErrorIsReported() = runTest {
        embeddingPath = null
        viewModel.addPdf("content://docs/x.pdf")
        val state = viewModel.uiState.first { it.error != null }
        assertEquals(ErrorKind.NO_MODEL, state.error)
    }

    @Test
    fun cannotAskWithoutQuestion() = runTest {
        viewModel.addPdf("content://docs/drone.pdf")
        val state = viewModel.uiState.first { it.selectedId != null }
        assertFalse(state.canAsk)
        viewModel.onQuestionChange("Weight?")
        assertTrue(viewModel.uiState.value.canAsk)
    }
}
