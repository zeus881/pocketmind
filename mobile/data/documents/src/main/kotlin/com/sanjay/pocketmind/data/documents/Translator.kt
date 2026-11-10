package com.sanjay.pocketmind.data.documents

import com.sanjay.pocketmind.core.common.result.AppResult
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.TokenEstimator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** A language the translator offers. [englishName] is what we put in the prompt. */
data class Language(val code: String, val englishName: String)

object Languages {
    val all = listOf(
        Language("en", "English"),
        Language("hi", "Hindi"),
        Language("bn", "Bengali"),
        Language("mr", "Marathi"),
        Language("ta", "Tamil"),
        Language("te", "Telugu"),
        Language("gu", "Gujarati"),
        Language("kn", "Kannada"),
        Language("ml", "Malayalam"),
        Language("pa", "Punjabi"),
        Language("ur", "Urdu"),
        Language("es", "Spanish"),
        Language("fr", "French"),
        Language("de", "German"),
        Language("pt", "Portuguese"),
        Language("it", "Italian"),
        Language("ru", "Russian"),
        Language("ar", "Arabic"),
        Language("zh", "Chinese"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
    )

    fun byCode(code: String): Language = all.firstOrNull { it.code == code } ?: all.first()
}

/**
 * Translates text with the on-device model.
 *
 * - A FRESH session per request (and per part): no chat history leaks into a translation.
 * - A strict prompt template ([PromptTemplates.translatePrompt]).
 * - Long texts are split at paragraph boundaries into parts that fit the context window and
 *   translated one after another, streamed as one continuous answer.
 */
class Translator @Inject constructor(private val task: LlmTask) {

    /** Loads the model; call before [translate]. */
    suspend fun prepare(): AppResult<Unit> = when (val loaded = task.ensureLoaded()) {
        is AppResult.Failure -> loaded
        is AppResult.Success -> AppResult.Success(Unit)
    }

    fun translate(text: String, from: Language, to: Language): Flow<String> = flow {
        val parts = splitIntoParts(text.trim(), partBudget(task.contextWindowTokens))
        parts.forEachIndexed { i, part ->
            if (i > 0) emit("\n\n")
            emitAll(
                task.stream(
                    PromptTemplates.TRANSLATE_SYSTEM,
                    PromptTemplates.translatePrompt(part, from.englishName, to.englishName),
                ),
            )
        }
    }

    companion object {
        private const val MAX_PART_TOKENS = 800

        /** 1/3 input, ~1/3 translation, the rest for prompt + safety margin. */
        private const val CONTEXT_SHARE_DIVISOR = 3

        /** Input share of the context; the translation needs about as many tokens as the input. */
        fun partBudget(contextWindow: Int): Int = minOf(MAX_PART_TOKENS, contextWindow / CONTEXT_SHARE_DIVISOR)

        /** Splits on blank lines; paragraphs that are still too long are split by sentences/words. */
        fun splitIntoParts(text: String, budget: Int): List<String> {
            if (TokenEstimator.estimate(text) <= budget) return listOf(text)
            val paragraphs = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
            val pages = paragraphs.mapIndexed { i, p -> PageText(i + 1, p) }
            // Reuse the chunker (no overlap: overlapping text would be translated twice).
            return TextChunker.chunk(pages, maxTokens = budget, overlapTokens = 0).map { it.text }
        }
    }
}
