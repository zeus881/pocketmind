package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExportTest {
    @Test
    fun `markdown has the title, speakers and messages in order`() {
        val md = ChatMarkdown.build(
            "Add numbers",
            listOf(
                Message("1", "c", TurnRole.USER, "Write C code ", 1),
                Message("2", "c", TurnRole.ASSISTANT, "```c\nint x;\n```", 2),
            ),
        )

        assertTrue(
            md.startsWith("# Add numbers\n\n**You:**\n\nWrite C code\n\n**PocketMind:**\n\n```c\nint x;\n```\n\n"),
        )
        assertTrue(md.trimEnd().endsWith("can be wrong."))
    }

    @Test
    fun `an untitled chat gets a default title`() {
        assertTrue(ChatMarkdown.build("  ", emptyList()).startsWith("# PocketMind chat"))
    }

    @Test
    fun `lines are cut into pages without splitting a line`() {
        // Ten lines of 20 units each; a page holds 50 units → 2 lines per page.
        val bottoms = (1..10).map { it * 20 }
        assertEquals(listOf(0..1, 2..3, 4..5, 6..7, 8..9), PdfPagination.pages(bottoms, pageHeight = 50))
    }

    @Test
    fun `a line taller than a page gets its own page and nothing loops`() {
        assertEquals(listOf(0..0, 1..1), PdfPagination.pages(listOf(100, 110), pageHeight = 50))
        assertEquals(emptyList<IntRange>(), PdfPagination.pages(emptyList(), pageHeight = 50))
    }
}
