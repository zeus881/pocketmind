package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.model.ChatTurn
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.core.testing.FakeLlmEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummarizerTest {
    private val turns = listOf(
        ChatTurn(TurnRole.USER, "My drone weighs 1.2 kg."),
        ChatTurn(TurnRole.ASSISTANT, "Then use 5-inch propellers."),
    )

    @Test
    fun `the model writes the summary in its own precise session`() = runTest {
        val engine = FakeLlmEngine(listOf(" The user's drone ", "weighs 1.2 kg. ")).apply { load("/m", true) }

        val summary = LlmConversationSummarizer(engine).summarize(null, turns)

        assertEquals("The user's drone weighs 1.2 kg.", summary)
        assertEquals(ChatSummaryPrompt.SYSTEM, engine.sessions.single().systemPrompt)
        assertEquals(SamplingParams.Precise, engine.sessions.single().sampling)
        assertTrue(engine.prompts.single().contains("User: My drone weighs 1.2 kg."))
    }

    @Test
    fun `a failing or empty generation gives null so the caller can fall back`() = runTest {
        val failing = FakeLlmEngine().apply {
            load("/m", true)
            generationError = IllegalStateException("native error")
        }
        assertNull(LlmConversationSummarizer(failing).summarize(null, turns))

        val empty = FakeLlmEngine(listOf("   ")).apply { load("/m", true) }
        assertNull(LlmConversationSummarizer(empty).summarize(null, turns))
    }

    @Test
    fun `no turns returns the previous summary without generating`() = runTest {
        val engine = FakeLlmEngine().apply { load("/m", true) }

        assertEquals("old", LlmConversationSummarizer(engine).summarize("old", emptyList()))
        assertTrue(engine.prompts.isEmpty())
    }

    @Test
    fun `the prompt continues a previous summary`() {
        val prompt = ChatSummaryPrompt.build("Drone is 1.2 kg.", turns)

        assertTrue(prompt.startsWith("Summary so far:\nDrone is 1.2 kg."))
        assertTrue(prompt.contains("More of the conversation:\nUser: My drone weighs 1.2 kg.\nAssistant: Then use"))
        assertTrue(prompt.endsWith("Write the updated summary."))
    }

    @Test
    fun `long turns are clipped and the oldest lines dropped when the transcript is too long`() {
        val long = List(40) { i -> ChatTurn(TurnRole.USER, "message $i " + "w".repeat(1_000)) }

        val prompt = ChatSummaryPrompt.build(null, long)

        assertTrue(prompt.length < ChatSummaryPrompt.MAX_TRANSCRIPT_CHARS + 200)
        assertTrue(prompt.contains("message 39")) // newest kept
        assertFalse(prompt.contains("message 0 ")) // oldest dropped
        assertFalse(prompt.contains("w".repeat(ChatSummaryPrompt.MAX_CHARS_PER_TURN + 1)))
    }
}
