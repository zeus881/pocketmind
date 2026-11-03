package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.rememberStreamingMarkdownState

/**
 * The app's one Markdown renderer for FINISHED text (saved chat answers, PDF summaries, PDF
 * answers). Before v2 each feature had its own copy (AUDIT L-6).
 *
 * - `retainState = true` keeps showing the previous rendering until the new text is parsed
 *   (plain `Markdown(content = …)` shows an empty frame on every update);
 * - the height animation is off, so a growing answer doesn't wobble;
 * - [immediate] = true parses synchronously: for text that is shown once, so it never flashes
 *   empty when it first appears (e.g. when a streamed answer becomes a saved message).
 *
 * For text that is still being written use [StreamingMarkdownText].
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, immediate: Boolean = false) {
    Markdown(
        markdownState = rememberMarkdownState(text, retainState = true, immediate = immediate),
        modifier = modifier,
        animations = markdownAnimations(animateTextSize = { this }),
    )
}

/**
 * Markdown for an answer that is still growing. [MarkdownText] re-parses and re-composes the WHOLE
 * text on every update (every 50 ms), so every paragraph above the cursor was laid out again and
 * the answer flickered on the phone. The renderer's streaming state is append-only: finished
 * blocks are parsed once and stay untouched, only the unfinished last block changes.
 *
 * [text] is the full answer so far. Only the new part is appended; if the text is ever replaced
 * rather than extended, the state starts over.
 */
@Composable
fun StreamingMarkdownText(text: String, modifier: Modifier = Modifier) {
    var generation by remember { mutableIntStateOf(0) }
    val latest by rememberUpdatedState(text)
    key(generation) {
        val state = rememberStreamingMarkdownState()
        LaunchedEffect(state) {
            var sent = ""
            // Collected one after another (never cancelled mid-append), so every piece arrives in order.
            snapshotFlow { latest }.collect { full ->
                when {
                    full.startsWith(sent) -> {
                        if (full.length > sent.length) state.append(full.substring(sent.length))
                        sent = full
                    }

                    else -> generation++
                }
            }
        }
        Markdown(
            state,
            modifier = modifier,
            animations = markdownAnimations(animateTextSize = { this }),
        )
    }
}
