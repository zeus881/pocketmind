package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * How the chat list scrolls, like current AI assistant apps (the owner's wish after the phone test):
 * - a chat opens at its newest text;
 * - a NEW question ([questionId]) moves to the TOP of the screen, and its answer is written downwards
 *   below it (the newest turn is at least one screen tall, see `MessageList`, so this is always possible);
 * - nothing else scrolls on its own: a growing answer never moves the text being read. When it gets
 *   longer than the screen, "jump to latest" appears and the user scrolls when they want to.
 */
@Composable
internal fun ChatScrollEffect(listState: LazyListState, questionIndex: Int, questionId: String?) {
    var shownQuestion by remember { mutableStateOf(questionId) }
    LaunchedEffect(listState) { listState.scrollToEnd() }
    LaunchedEffect(questionId) {
        if (questionId != null && questionId != shownQuestion && questionIndex >= 0) {
            shownQuestion = questionId
            listState.animateScrollToItem(questionIndex)
        }
    }
}

/** Scrolls to the very end of the last item (an answer can be taller than the screen). */
internal suspend fun LazyListState.scrollToEnd() {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == last }) scrollToItem(last)
    var steps = 0
    while (canScrollForward && steps++ < MAX_SCROLL_STEPS) {
        scrollBy(layoutInfo.viewportSize.height.coerceAtLeast(1).toFloat())
    }
}

/** A guard against an endless loop; one step is a full screen, so 50 covers any answer. */
private const val MAX_SCROLL_STEPS = 50
