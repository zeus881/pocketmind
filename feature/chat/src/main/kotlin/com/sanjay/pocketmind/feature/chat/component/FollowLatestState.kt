package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * "Follow the newest text" for the chat list, like a terminal or a chat app:
 * - while [following], the list stays at its end: a new message, a growing answer or the keyboard
 *   opening (a smaller viewport) scrolls it there again;
 * - the moment the user drags towards older messages, following stops and NOTHING moves under their
 *   finger any more, even while an answer is still being written below (found on the phone: the old
 *   bottom-anchored list pushed the text up every 50 ms, so it couldn't be read while it was written);
 * - following starts again when the user scrolls back to the end, or taps "jump to latest".
 *
 * Attach [connection] with `Modifier.nestedScroll` to a parent of the list, and run [FollowLatestEffect].
 */
@Stable
internal class FollowLatestState {
    var following by mutableStateOf(true)
        private set

    /** Sees the user's own drags (not programmatic scrolls): towards older messages = stop following. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Positive y: the finger moves down, so the content moves towards the start (older messages).
            if (source == NestedScrollSource.UserInput && available.y > 0f) following = false
            return Offset.Zero
        }
    }

    fun follow() {
        following = true
    }

    internal fun onEndReached() {
        following = true
    }
}

@Composable
internal fun rememberFollowLatestState(): FollowLatestState = remember { FollowLatestState() }

/** Keeps [listState] at its end while [follow] is following (see [FollowLatestState]). */
@Composable
internal fun FollowLatestEffect(listState: LazyListState, follow: FollowLatestState) {
    LaunchedEffect(listState, follow) {
        snapshotFlow { follow.following to listState.canScrollForward }.collect { (following, canScrollForward) ->
            when {
                !canScrollForward -> follow.onEndReached()

                // at the newest content: follow (again)
                following -> listState.scrollToEndUnlessTouched()

                else -> Unit // the user is reading older text: leave the list where it is
            }
        }
    }
}

/**
 * Scrolls to the very end of the last item (an answer can be taller than the screen). A drag that
 * starts at the same moment wins: the list's scroll lock cancels this scroll, which is expected and
 * must not end the follow coroutine.
 */
@Suppress("SwallowedException") // see above: only the scroll was taken over, the coroutine goes on
private suspend fun LazyListState.scrollToEndUnlessTouched() {
    try {
        val last = layoutInfo.totalItemsCount - 1
        if (last < 0) return
        if (layoutInfo.visibleItemsInfo.none { it.index == last }) scrollToItem(last)
        var steps = 0
        while (canScrollForward && steps++ < MAX_SCROLL_STEPS) {
            scrollBy(layoutInfo.viewportSize.height.coerceAtLeast(1).toFloat())
        }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive() // our own coroutine was cancelled: stop for real
    }
}

/** A guard against an endless loop; one step is a full screen, so 50 covers any answer. */
private const val MAX_SCROLL_STEPS = 50
