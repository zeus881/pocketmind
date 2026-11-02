package com.sanjay.pocketmind.feature.chat

import androidx.compose.runtime.Composable
import com.sanjay.pocketmind.core.llm.FailureReason
import com.sanjay.pocketmind.core.model.BranchInfo
import com.sanjay.pocketmind.core.model.Message
import com.sanjay.pocketmind.core.model.TurnRole
import com.sanjay.pocketmind.core.testing.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of the chat screen's states (record: `./gradlew :feature:chat:recordRoborazziDebug`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenshotTest {

    @Composable
    private fun Chat(state: ChatUiState) {
        ChatContent(
            state = state,
            onSend = {},
            onStop = {},
            onRetry = {},
            onNewChat = {},
            onReloadModel = {},
            onOpenModels = {},
        )
    }

    @Test
    fun emptyChat() = captureScreenshots("chat_empty") {
        Chat(ChatUiState(model = ModelUi.Ready("GPU")))
    }

    @Test
    fun conversationWithCode() = captureScreenshots("chat_conversation") {
        Chat(
            ChatUiState(
                chatId = "c1",
                title = "Add two numbers in C",
                messages = listOf(
                    Message("1", "c1", TurnRole.USER, "Write a C program to add two numbers", 1),
                    Message(
                        "2",
                        "c1",
                        TurnRole.ASSISTANT,
                        "Here is a complete program:\n\n```c\n#include <stdio.h>\n\nint main(void) {\n" +
                            "    int a = 5, b = 7;\n    printf(\"%d\\n\", a + b);\n    return 0;\n}\n```\n\n" +
                            "It prints **12**.",
                        2,
                        tokensPerSecond = 26.3,
                        backend = "GPU",
                    ),
                ),
                // The answer was regenerated once: "‹ 2 / 2 ›" under it.
                branches = mapOf("2" to BranchInfo(index = 1, count = 2)),
                model = ModelUi.Ready("GPU"),
            ),
        )
    }

    @Test
    fun streamingAnswer() = captureScreenshots("chat_streaming") {
        Chat(
            ChatUiState(
                chatId = "c1",
                messages = listOf(Message("1", "c1", TurnRole.USER, "Explain how drones stay level", 1)),
                streamingText = "A flight controller reads the **gyroscope** about 1000 times per second and",
                isGenerating = true,
                model = ModelUi.Ready("GPU"),
            ),
        )
    }

    @Test
    fun modelCrashedLastTime() = captureScreenshots("chat_model_crashed") {
        Chat(ChatUiState(model = ModelUi.Failed(FailureReason.CRASHED_LAST_TIME)))
    }
}
