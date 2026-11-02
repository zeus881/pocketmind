package com.sanjay.pocketmind.data.chat

import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tree used below (createdAt in brackets):
 * ```
 * q1[1] ─ a1[2] ─ q2[3] ─ a2[4]
 *              └─ q2b[5] ─ a2b[6]      (q2 was edited → q2b)
 *                       └─ a2c[7]      (a2b was regenerated → a2c)
 * ```
 */
class ConversationTreeTest {
    private fun m(id: String, parent: String?, at: Long, role: TurnRole = TurnRole.USER) =
        Message(id, "c", role, id, at, parentId = parent)

    private val all = listOf(
        m("q1", null, 1),
        m("a1", "q1", 2, TurnRole.ASSISTANT),
        m("q2", "a1", 3),
        m("a2", "q2", 4, TurnRole.ASSISTANT),
        m("q2b", "a1", 5),
        m("a2b", "q2b", 6, TurnRole.ASSISTANT),
        m("a2c", "q2b", 7, TurnRole.ASSISTANT),
    )

    @Test
    fun `the path runs from the root to the current leaf`() {
        assertEquals(listOf("q1", "a1", "q2b", "a2c"), ConversationTree.path(all, "a2c").map { it.id })
        assertEquals(listOf("q1", "a1", "q2", "a2"), ConversationTree.path(all, "a2").map { it.id })
    }

    @Test
    fun `an unknown leaf falls back to the newest message and an empty chat stays empty`() {
        assertEquals("a2c", ConversationTree.path(all, "nope").last().id)
        assertEquals(emptyList<Message>(), ConversationTree.path(emptyList(), null))
    }

    @Test
    fun `branch info numbers the versions on the path`() {
        val path = ConversationTree.path(all, "a2c")
        assertEquals(
            mapOf("q2b" to BranchInfo(1, 2), "a2c" to BranchInfo(1, 2)),
            ConversationTree.branchInfo(all, path),
        )
    }

    @Test
    fun `switching to a version continues on its newest branch`() {
        assertEquals("a2", ConversationTree.newestLeafUnder(all, "q2"))
        assertEquals("a2c", ConversationTree.newestLeafUnder(all, "q2b"))
        assertEquals("a2b", ConversationTree.newestLeafUnder(all, "a2b"))
    }

    @Test
    fun `siblings are the versions with the same parent`() {
        assertEquals(listOf("q2", "q2b"), ConversationTree.siblings(all, all.first { it.id == "q2b" }).map { it.id })
    }

    @Test
    fun `a corrupt cycle does not loop forever`() {
        val cyclic = listOf(m("x", "y", 1), m("y", "x", 2))
        assertEquals(2, ConversationTree.path(cyclic, "y").size)
        ConversationTree.newestLeafUnder(listOf(m("x", "x", 1)), "x")
    }
}
