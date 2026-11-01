package com.manjit.pocketmind.data.documents

import com.manjit.pocketmind.core.common.result.AppResult
import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.core.llm.InferenceSettings
import com.manjit.pocketmind.core.llm.InferenceSettingsSource
import com.manjit.pocketmind.core.llm.LlmTask
import com.manjit.pocketmind.core.llm.SamplingParams
import com.manjit.pocketmind.core.testing.FakeLlmEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplatesTest {
    @Test
    fun translatePromptHasStrictRulesAndNoLeadingSpaces() {
        val prompt = PromptTemplates.translatePrompt("Hello\nWorld", "English", "Hindi")
        assertTrue(prompt.startsWith("Translate the text from English to Hindi."))
        assertTrue(prompt.contains("output only the translation"))
        assertTrue(prompt.endsWith("Hello\nWorld"))
        // Regression test for the trimIndent() pitfall: no instruction line may be indented.
        prompt.lines().forEach { assertFalse("indented: '$it'", it.startsWith(" ")) }
    }

    @Test
    fun mapPromptNamesThePart() {
        val prompt = PromptTemplates.mapPrompt("text", part = 2, totalParts = 7)
        assertTrue(prompt.contains("part 2 of 7"))
    }

    @Test
    fun reducePromptNumbersParts() {
        val prompt = PromptTemplates.reducePrompt(listOf("- a", "- b"), isFinal = true)
        assertTrue(prompt.contains("Part 1:\n- a"))
        assertTrue(prompt.contains("Part 2:\n- b"))
        assertTrue(prompt.contains("final summary"))
    }
}

class SummarizerTest {
    private val engine = FakeLlmEngine(responsePieces = listOf("- point"))
    private var settings: InferenceSettings? = InferenceSettings("/m", true, "S", SamplingParams())
    private val task = LlmTask(
        engine,
        object : InferenceSettingsSource {
            override suspend fun current() = settings
        },
    )

    private fun longDocument(pages: Int) = (1..pages).map { p ->
        PageText(p, (1..40).joinToString(" ") { "Page $p sentence $it describes a drone subsystem." })
    }

    @Test
    fun shortDocumentUsesOnePass() = runTest {
        val progress = Summarizer(task).summarize(listOf(PageText(1, "A short note."))).toList()
        assertEquals(SummaryProgress.Done("- point", parts = 1), progress.last())
        assertEquals(1, engine.prompts.size)
    }

    @Test
    fun longDocumentMapsEveryChunkThenReduces() = runTest {
        val progress = Summarizer(task).summarize(longDocument(30)).toList()

        val mapping = progress.filterIsInstance<SummaryProgress.Mapping>()
        assertTrue("expected several parts", mapping.size > 3)
        assertEquals((1..mapping.size).toList(), mapping.map { it.part })
        assertTrue(progress.any { it is SummaryProgress.Reducing })
        val done = progress.last() as SummaryProgress.Done
        assertEquals(mapping.size, done.parts)
        // Every LLM call used a fresh session (no history leaking between parts).
        assertTrue(engine.sessions.all { it.history.isEmpty() })
        assertEquals(engine.prompts.size, engine.sessions.size)
    }

    @Test
    fun noModelFails() = runTest {
        settings = null
        val progress = Summarizer(task).summarize(longDocument(2)).toList()
        assertEquals(SummaryProgress.Failed(ErrorKind.NO_MODEL), progress.last())
    }

    @Test
    fun groupingRespectsTheBudget() {
        val groups = Summarizer.groupByBudget(List(10) { "x".repeat(400) }, budget = 250) // ~100 tokens each
        assertEquals(5, groups.size)
        assertTrue(groups.all { it.size == 2 })
    }
}

class TranslatorTest {
    private val engine = FakeLlmEngine(responsePieces = listOf("नमस्ते"))
    private val task = LlmTask(
        engine,
        object : InferenceSettingsSource {
            override suspend fun current() = InferenceSettings("/m", true, "S", SamplingParams())
        },
    )

    @Test
    fun translatesInAFreshSessionWithTheStrictTemplate() = runTest {
        val translator = Translator(task)
        assertTrue(translator.prepare() is AppResult.Success)

        val out = translator.translate("Hello", Languages.byCode("en"), Languages.byCode("hi")).toList()

        assertEquals("नमस्ते", out.joinToString(""))
        assertTrue(engine.prompts.single().startsWith("Translate the text from English to Hindi."))
        assertEquals(PromptTemplates.TRANSLATE_SYSTEM, engine.sessions.single().systemPrompt)
        assertTrue(engine.sessions.single().history.isEmpty())
    }

    @Test
    fun longTextIsSplitIntoParts() {
        val text = (1..30).joinToString("\n\n") { "Paragraph $it. " + "Some words here. ".repeat(20) }
        val parts = Translator.splitIntoParts(text, budget = 300)
        assertTrue(parts.size > 1)
    }

    @Test
    fun shortTextIsOnePart() {
        assertEquals(listOf("Hi there"), Translator.splitIntoParts("Hi there", budget = 300))
    }
}
