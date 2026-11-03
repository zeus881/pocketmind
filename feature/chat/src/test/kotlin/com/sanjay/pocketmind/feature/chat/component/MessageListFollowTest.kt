package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The list follows a growing answer only while the user is at the end. Phone report (2026-11-03): with
 * the old bottom-anchored list, every new line pushed the text up, so it couldn't be read while written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MessageListFollowTest {
    @get:Rule
    val compose = createComposeRule()

    private val history = (1..12).flatMap { n ->
        listOf(
            Message("q$n", "c", TurnRole.USER, "Question $n", n * 2L),
            Message("a$n", "c", TurnRole.ASSISTANT, "Answer number $n.\n\n" + filler(n), n * 2L + 1),
        )
    }

    private var live by mutableStateOf<String?>(null)

    private fun show() = compose.setContent {
        MessageList(
            messages = history,
            streamingText = live,
            canRetry = live == null,
            onRetry = {},
            modifier = Modifier.testTag(LIST),
        )
    }

    @Test
    fun `opens at the newest message and follows a growing answer`() {
        live = "Line 1."
        show()
        compose.onNodeWithText("Line 1.").assertIsDisplayed()

        live = (1..40).joinToString("\n\n") { "Line $it." }
        compose.waitForIdle()

        compose.onNodeWithText("Line 40.").assertIsDisplayed()
        compose.onNodeWithContentDescription(JUMP).assertDoesNotExist()
    }

    @Test
    fun `after scrolling up the text being read stays still while the answer grows`() {
        live = (1..10).joinToString("\n\n") { "Line $it." }
        show()
        compose.onNodeWithTag(LIST).performTouchInput { swipeDown(startY = centerY, endY = centerY + SWIPE_PX) }
        compose.waitForIdle()
        val (text, before) = firstVisibleText()

        live = (1..40).joinToString("\n\n") { "Line $it." }
        compose.waitForIdle()

        assertEquals(before, compose.onNodeWithText(text).getBoundsInRoot().top)
        compose.onNodeWithText("Line 40.").assertDoesNotExist()
        compose.onNodeWithContentDescription(JUMP).assertIsDisplayed()
    }

    @Test
    fun `jump to latest goes back to the end and follows again`() {
        live = (1..10).joinToString("\n\n") { "Line $it." }
        show()
        compose.onNodeWithTag(LIST).performTouchInput { swipeDown(startY = centerY, endY = centerY + SWIPE_PX) }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(JUMP).performClick()
        compose.waitForIdle()
        live = (1..30).joinToString("\n\n") { "Line $it." }
        compose.waitForIdle()

        compose.onNodeWithText("Line 30.").assertIsDisplayed()
        compose.onNodeWithContentDescription(JUMP).assertDoesNotExist()
    }

    /** The topmost text on screen (whatever the swipe landed on) and its position. */
    private fun firstVisibleText(): Pair<String, Dp> {
        val node = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.top > 0f }
            .minBy { it.boundsInRoot.top }
        val text = node.config[SemanticsProperties.Text].joinToString("")
        return text to compose.onNodeWithText(text).getBoundsInRoot().top
    }

    private companion object {
        const val SWIPE_PX = 600f
        const val LIST = "messages"
        const val JUMP = "Jump to the latest message"

        fun filler(n: Int) = (1..4).joinToString("\n\n") { "Paragraph $it of answer $n with some text in it." }
    }
}
