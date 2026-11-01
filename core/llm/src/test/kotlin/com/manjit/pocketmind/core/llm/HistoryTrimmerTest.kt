package com.manjit.pocketmind.core.llm

import com.manjit.pocketmind.core.model.ChatTurn
import com.manjit.pocketmind.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTrimmerTest {
    private fun user(text: String) = ChatTurn(TurnRole.USER, text)

    private fun bot(text: String) = ChatTurn(TurnRole.ASSISTANT, text)

    @Test
    fun `short history is kept whole with no note`() {
        val history = listOf(user("Hi"), bot("Hello! How can I help?"))
        val result = HistoryTrimmer.trim(history, budgetTokens = 1000)
        assertEquals(history, result.turns)
        assertNull(result.summaryNote)
    }

    @Test
    fun `long history keeps the newest turns and summarizes the oldest`() {
        val history = listOf(
            user("Tell me about drones. " + "x".repeat(2000)),
            bot("Drones are aircraft without a pilot. " + "y".repeat(2000)),
            user("And batteries?"),
            bot("Lithium polymer is common."),
        )
        // ~500 tokens per old turn; a budget of 200 fits only the two newest turns.
        val result = HistoryTrimmer.trim(history, budgetTokens = 200)

        assertEquals(listOf(user("And batteries?"), bot("Lithium polymer is common.")), result.turns)
        val note = result.summaryNote
        assertNotNull(note)
        assertTrue(note!!.startsWith("Earlier in this conversation:"))
        assertTrue(note.contains("Drones are aircraft without a pilot."))
    }

    @Test
    fun `kept history never starts with an assistant turn`() {
        val history = listOf(user("q1 " + "x".repeat(300)), bot("a1"), user("q2"), bot("a2"))
        // Budget fits a1, q2, a2 but not q1 — a1 would be an orphan reply.
        val result = HistoryTrimmer.trim(history, budgetTokens = 10)
        assertEquals(TurnRole.USER, result.turns.first().role)
    }

    @Test
    fun `zero budget keeps nothing`() {
        val result = HistoryTrimmer.trim(listOf(user("hi")), budgetTokens = 0)
        assertTrue(result.turns.isEmpty())
    }
}
