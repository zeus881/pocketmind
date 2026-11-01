package com.manjit.pocketmind.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTitlesTest {
    @Test
    fun `short first line becomes the title without trailing punctuation`() {
        assertEquals("What is LiteRT-LM", ChatTitles.fromFirstMessage("What is LiteRT-LM?"))
    }

    @Test
    fun `only the first non-empty line is used and whitespace is collapsed`() {
        assertEquals("Plan my trip", ChatTitles.fromFirstMessage("\n\n  Plan   my trip\nto Goa please"))
    }

    @Test
    fun `long text is cut at a word boundary with an ellipsis`() {
        val title = ChatTitles.fromFirstMessage(
            "Explain how quantization reduces the memory needed by large language models",
        )
        assertTrue(title.endsWith("…"))
        assertTrue(title.length <= ChatTitles.MAX_LENGTH + 1)
        assertEquals("Explain how quantization reduces the…", title)
    }
}
