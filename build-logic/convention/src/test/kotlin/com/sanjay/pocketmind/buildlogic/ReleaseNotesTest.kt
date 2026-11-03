package com.sanjay.pocketmind.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun `only user-facing commits become notes, with plain labels`() {
        val notes = ReleaseNotes.fromCommits(
            listOf(
                "feat: ask about a photo with on-device vision",
                "fix(chat): keep the draft when rotating",
                "perf: faster chat list",
                "chore: bump KSP",
                "docs: lesson 17",
                "test: more cases",
                "Merge branch 'v2'",
            ),
        )

        assertEquals(
            "• New: Ask about a photo with on-device vision\n" +
                "• Fixed: Keep the draft when rotating\n" +
                "• Faster: Faster chat list",
            notes,
        )
    }

    @Test
    fun `without user-facing commits the note stays honest and short`() {
        assertEquals("Small improvements and fixes.", ReleaseNotes.fromCommits(listOf("chore: x", "ci: y")))
    }

    @Test
    fun `notes never exceed the Play limit and end with a hint when cut`() {
        val many = (1..40).map { "feat: feature number $it with a fairly long description" }

        val notes = ReleaseNotes.fromCommits(many)

        assertTrue(notes.length <= ReleaseNotes.PLAY_LIMIT)
        assertTrue(notes.endsWith("• …and more improvements."))
        assertTrue(notes.startsWith("• New: Feature number 1 "))
    }

    @Test
    fun `breaking-change marks and duplicates are handled`() {
        assertEquals(
            "• New: Encrypted chats",
            ReleaseNotes.fromCommits(listOf("feat!: encrypted chats", "feat: encrypted chats")),
        )
    }
}
