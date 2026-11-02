package com.sanjay.pocketmind.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `markdown formatting is removed and code is summarised`() {
        val md = "## Steps\n\n- **Open** the [manual](https://x.y)\n- Run:\n\n" +
            "```c\nint x = 1;\n```\n\n> Note: `x` is *one*."

        val spoken = SpeechText.fromMarkdown(md)

        assertTrue(spoken, spoken.startsWith("Steps"))
        assertTrue(spoken, "Open the manual" in spoken)
        assertTrue(spoken, "Code block omitted." in spoken)
        assertTrue(spoken, "Note: x is one." in spoken)
        // No formatting characters, no URL, no code read out.
        assertTrue(spoken, spoken.none { it in "#*_`[]>" })
        assertTrue(spoken, "https" !in spoken && "int x" !in spoken)
    }

    @Test
    fun `long text is split at sentence ends within the limit`() {
        val text = "First sentence here. Second one is a bit longer. Third."
        val pieces = SpeechText.chunks(text, maxLength = 30)
        assertEquals(listOf("First sentence here.", "Second one is a bit longer.", "Third."), pieces)
        assertTrue(pieces.all { it.length <= 30 })
    }

    @Test
    fun `text without sentence ends is split at spaces, and a giant word is cut`() {
        assertEquals(listOf("aaa bbb", "ccc"), SpeechText.chunks("aaa bbb ccc", maxLength = 8))
        assertEquals(listOf("abcd", "efgh", "ij"), SpeechText.chunks("abcdefghij", maxLength = 4))
    }

    @Test
    fun `recognizer error codes map to user-facing kinds`() {
        assertEquals(SpeechError.NOTHING_HEARD, SpeechErrors.map(7))
        assertEquals(SpeechError.NOTHING_HEARD, SpeechErrors.map(6))
        assertEquals(SpeechError.PERMISSION, SpeechErrors.map(9))
        assertEquals(SpeechError.UNAVAILABLE, SpeechErrors.map(13))
        assertEquals(SpeechError.FAILED, SpeechErrors.map(3))
    }
}
