package com.manjit.pocketmind.feature.translate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manjit.pocketmind.core.common.flow.throttleLatest
import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.data.documents.Language
import com.manjit.pocketmind.data.documents.Languages
import com.manjit.pocketmind.data.documents.Translator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class TranslateUiState(
    val from: Language = Languages.byCode("en"),
    val to: Language = Languages.byCode("hi"),
    val input: String = "",
    val output: String = "",
    val isTranslating: Boolean = false,
    val error: ErrorKind? = null,
) {
    val canTranslate: Boolean get() = input.isNotBlank() && !isTranslating && from != to
}

/**
 * Translate screen. Every request runs in a FRESH model session (see [Translator]); the
 * output streams in, throttled to 20 UI updates per second like the chat.
 *
 * The chosen languages are kept in [SavedStateHandle], so they survive process death.
 */
@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val translator: Translator,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TranslateUiState(
            from = Languages.byCode(savedState[KEY_FROM] ?: "en"),
            to = Languages.byCode(savedState[KEY_TO] ?: "hi"),
        ),
    )
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text, error = null) }

    fun setFrom(language: Language) {
        savedState[KEY_FROM] = language.code
        _uiState.update { it.copy(from = language) }
    }

    fun setTo(language: Language) {
        savedState[KEY_TO] = language.code
        _uiState.update { it.copy(to = language) }
    }

    /** Swaps the languages; if there is a translation, it becomes the new input. */
    fun swap() {
        val s = _uiState.value
        savedState[KEY_FROM] = s.to.code
        savedState[KEY_TO] = s.from.code
        _uiState.value = s.copy(
            from = s.to,
            to = s.from,
            input = s.output.ifBlank { s.input },
            output = if (s.output.isBlank()) "" else s.input,
        )
    }

    @Suppress("TooGenericExceptionCaught") // any engine failure becomes a readable error
    fun translate() {
        val s = _uiState.value
        if (!s.canTranslate) return
        job = viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, output = "", error = null) }
            val prepared = translator.prepare()
            if (prepared is AppResult.Failure) {
                _uiState.update { it.copy(isTranslating = false, error = prepared.kind) }
                return@launch
            }
            try {
                translator.translate(s.input, s.from, s.to)
                    .runningFold("") { acc, piece -> acc + piece }
                    .throttleLatest(UI_THROTTLE_MS)
                    .collect { text -> _uiState.update { it.copy(output = text) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Translation failed")
                _uiState.update { it.copy(error = ErrorKind.GENERATION_FAILED) }
            } finally {
                _uiState.update { it.copy(isTranslating = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private companion object {
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val UI_THROTTLE_MS = 50L
    }
}
