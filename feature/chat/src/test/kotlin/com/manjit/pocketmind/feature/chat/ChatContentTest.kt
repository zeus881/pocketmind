package com.manjit.pocketmind.feature.chat

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.manjit.pocketmind.core.model.Message
import com.manjit.pocketmind.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Compose UI tests, run on the JVM with Robolectric (no phone needed). */
@RunWith(RobolectricTestRunner::class)
class ChatContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: ChatUiState, onSend: (String) -> Unit = {}, onStop: () -> Unit = {}) {
        compose.setContent {
            ChatContent(
                state = state,
                onSend = onSend,
                onStop = onStop,
                onRetry = {},
                onNewChat = {},
                onReloadModel = {},
                onOpenModels = {},
            )
        }
    }

    @Test
    fun sendIsDisabledUntilTextIsTyped_thenSendsTheText() {
        var sent: String? = null
        setContent(ChatUiState(model = ModelUi.Ready("GPU")), onSend = { sent = it })

        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        compose.onNodeWithText("Ask anything — it stays on your phone").performTextInput("Hi")
        compose.onNodeWithContentDescription("Send message").performClick()

        assertEquals("Hi", sent)
    }

    @Test
    fun stopButtonIsShownWhileGenerating() {
        var stopped = false
        val generating = ChatUiState(model = ModelUi.Ready("GPU"), streamingText = "Hel", isGenerating = true)
        setContent(generating, onStop = { stopped = true })

        compose.onNodeWithContentDescription("Stop generating").performClick()

        assertTrue(stopped)
    }

    @Test
    fun emptyChatShowsTheEmptyState() {
        setContent(ChatUiState(model = ModelUi.Ready("GPU")))
        compose.onNodeWithText("Start a private conversation").assertExists()
    }

    @Test
    fun noModelShowsGetAModel() {
        setContent(ChatUiState(model = ModelUi.NoModel))
        compose.onNodeWithText("Get a model").assertExists()
    }

    @Test
    fun assistantMessageShowsSpeedLabel() {
        val messages = listOf(
            Message("1", "c", TurnRole.USER, "Hello", 1),
            Message("2", "c", TurnRole.ASSISTANT, "Hi", 2, tokensPerSecond = 12.4, backend = "GPU"),
        )
        setContent(ChatUiState(model = ModelUi.Ready("GPU"), messages = messages))
        compose.onNodeWithText("12.4 tok/s · GPU").assertExists()
    }
}
