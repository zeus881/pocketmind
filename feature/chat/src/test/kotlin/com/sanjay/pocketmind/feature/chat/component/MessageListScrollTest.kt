package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolling like current AI assistant apps (the owner's wish after the 2026-11-03 phone test): a new
 * question moves to the top, its answer is written downwards below it, and nothing scrolls on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MessageListScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val history = (1..12).flatMap { n ->
        listOf(
            Message("q$n", "c", TurnRole.USER, "Question $n", n * 2L),
            Message("a$n", "c", TurnRole.ASSISTANT, "Answer number $n.\n\n" + filler(n), n * 2L + 1),
        )
    }

    private var messages by mutableStateOf(history)
    private var live by mutableStateOf<String?>(null)

    private fun show() = compose.setContent {
        MessageList(
            messages = messages,
            streamingText = live,
            canRetry = live == null,
            onRetry = {},
            modifier = Modifier.testTag(LIST),
        )
    }

    /** What happens when the user sends: the answer starts and the question is saved. */
    private fun ask(question: String) {
        live = ""
        messages = messages + Message("new", "c", TurnRole.USER, question, NEW_TIME)
        compose.waitForIdle()
    }

    @Test
    fun `a chat opens at its newest text`() {
        show()
        compose.onNodeWithText("Paragraph 4 of answer 12 with some text in it.").assertIsDisplayed()
        compose.onNodeWithContentDescription(JUMP).assertDoesNotExist()
    }

    @Test
    fun `a new question moves to the top and its answer is written below it`() {
        show()
        ask("Tell me a story")
        val listTop = compose.onNodeWithTag(LIST).getBoundsInRoot().top
        val questionTop = compose.onNodeWithText("Tell me a story").getBoundsInRoot().top
        assertTrue("question at the top, was ${questionTop - listTop}", questionTop - listTop < 60.dp)

        live = "Once upon a time."
        compose.waitForIdle()
        val firstLine = compose.onNodeWithText("Once upon a time.")
        firstLine.assertIsDisplayed()
        val lineTop = firstLine.getBoundsInRoot().top
        assertTrue(lineTop > questionTop) // below the question…
        assertTrue("near the top, was ${lineTop - listTop}", lineTop - listTop < 200.dp) // …not at the bottom
        compose.onNodeWithContentDescription(JUMP).assertDoesNotExist() // a short answer fits
    }

    @Test
    fun `a long answer never moves the question and offers jump to latest`() {
        show()
        ask("Tell me a story")
        val top = compose.onNodeWithText("Tell me a story").getBoundsInRoot().top

        live = (1..40).joinToString("\n\n") { "Line $it." }
        compose.waitForIdle()

        assertEquals(top, compose.onNodeWithText("Tell me a story").getBoundsInRoot().top)
        compose.onNodeWithText("Line 40.").assertIsNotDisplayed() // below the screen, not scrolled to
        compose.onNodeWithContentDescription(JUMP).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Line 40.").assertIsDisplayed()
        compose.onNodeWithContentDescription(JUMP).assertDoesNotExist()
    }

    private companion object {
        const val LIST = "messages"
        const val JUMP = "Jump to the latest message"
        const val NEW_TIME = 100L

        fun filler(n: Int) = (1..4).joinToString("\n\n") { "Paragraph $it of answer $n with some text in it." }
    }
}
