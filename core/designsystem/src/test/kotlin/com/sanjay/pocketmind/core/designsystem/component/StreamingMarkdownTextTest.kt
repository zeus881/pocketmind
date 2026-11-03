package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamingMarkdownTextTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `growing text keeps earlier blocks and shows the new part`() {
        var text by mutableStateOf("First paragraph.")
        compose.setContent { StreamingMarkdownText(text) }
        compose.waitUntil { compose.onAllNodesWithTextCount("First paragraph.") > 0 }

        text = "First paragraph.\n\nSecond **bold** paragraph."
        compose.waitUntil { compose.onAllNodesWithTextCount("Second bold paragraph.") > 0 }

        compose.onNodeWithText("First paragraph.").assertIsDisplayed()
        compose.onNodeWithText("Second bold paragraph.").assertIsDisplayed()
    }

    @Test
    fun `replaced text starts over instead of appending`() {
        var text by mutableStateOf("Old answer.")
        compose.setContent { StreamingMarkdownText(text) }
        compose.waitUntil { compose.onAllNodesWithTextCount("Old answer.") > 0 }

        text = "New answer."
        compose.waitUntil { compose.onAllNodesWithTextCount("New answer.") > 0 }

        compose.waitUntil { compose.onAllNodesWithTextCount("Old answer.") == 0 }
    }

    private fun ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
