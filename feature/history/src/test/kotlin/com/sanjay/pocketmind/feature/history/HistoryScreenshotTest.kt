package com.sanjay.pocketmind.feature.history

import com.sanjay.pocketmind.core.model.Chat
import com.sanjay.pocketmind.core.model.ChatSummary
import com.sanjay.pocketmind.core.testing.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Golden images of History (record: `./gradlew :feature:history:recordRoborazziDebug`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenshotTest {
    private fun summary(id: String, title: String, last: String) = ChatSummary(Chat(id, title, 1, 1), last)

    @Test
    fun chats() = captureScreenshots("history_chats") {
        HistoryContent(
            state = HistoryUiState(
                list = ListState.Content(
                    listOf(
                        summary("1", "Add two numbers in C", "It prints 12."),
                        summary("2", "Healthy breakfast ideas", "1. Oats with fruit…"),
                        summary("3", "How GPS works", "Satellites send time signals…"),
                    ),
                ),
            ),
            onQueryChange = {},
            onOpenChat = {},
            onRename = { _, _ -> },
            onDelete = {},
            onRetry = {},
        )
    }

    @Test
    fun empty() = captureScreenshots("history_empty") {
        HistoryContent(
            state = HistoryUiState(list = ListState.Content(emptyList())),
            onQueryChange = {},
            onOpenChat = {},
            onRename = { _, _ -> },
            onDelete = {},
            onRetry = {},
        )
    }
}
