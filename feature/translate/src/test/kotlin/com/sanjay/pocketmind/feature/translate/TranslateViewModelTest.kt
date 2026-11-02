package com.sanjay.pocketmind.feature.translate

import androidx.lifecycle.SavedStateHandle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.llm.InferenceSettings
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import com.sanjay.pocketmind.core.testing.MainDispatcherRule
import com.sanjay.pocketmind.data.documents.Languages
import com.sanjay.pocketmind.data.documents.Translator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val engine = FakeLlmEngine(responsePieces = listOf("नमस्ते", " दुनिया"))
    private var settings: InferenceSettings? = InferenceSettings("/m", true, "S", SamplingParams())
    private val savedState = SavedStateHandle()

    private val viewModel by lazy {
        val task = LlmTask(
            engine,
            object : InferenceSettingsSource {
                override suspend fun current() = settings
            },
        )
        TranslateViewModel(Translator(task), savedState)
    }

    @Test
    fun translatesAndStreamsTheOutput() = runTest {
        viewModel.onInputChange("Hello world")
        viewModel.translate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("नमस्ते दुनिया", state.output)
        assertFalse(state.isTranslating)
    }

    @Test
    fun swapExchangesLanguagesAndTexts() = runTest {
        viewModel.onInputChange("Hello world")
        viewModel.translate()
        advanceUntilIdle()

        viewModel.swap()

        val state = viewModel.uiState.value
        assertEquals("hi", state.from.code)
        assertEquals("en", state.to.code)
        assertEquals("नमस्ते दुनिया", state.input)
        assertEquals("Hello world", state.output)
        assertEquals("hi", savedState.get<String>("from"))
    }

    @Test
    fun noModelShowsError() = runTest {
        settings = null
        viewModel.onInputChange("Hello")
        viewModel.translate()
        advanceUntilIdle()
        assertEquals(ErrorKind.NO_MODEL, viewModel.uiState.value.error)
    }

    @Test
    fun sameLanguageOrBlankInputCannotTranslate() = runTest {
        assertFalse(viewModel.uiState.value.canTranslate)
        viewModel.onInputChange("Hi")
        viewModel.setTo(Languages.byCode("en"))
        assertFalse(viewModel.uiState.value.canTranslate)
    }
}
