package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * The app's one Markdown renderer (chat answers, PDF summaries, PDF answers). Before v2 each
 * feature had its own copy (AUDIT L-6).
 *
 * Built not to flicker while text streams in:
 * - `retainState = true` keeps showing the previous rendering until the new text is parsed
 *   (plain `Markdown(content = …)` shows an empty frame on every update);
 * - the height animation is off, so a growing answer doesn't wobble on every token;
 * - [immediate] = true parses synchronously: for FINISHED text that is shown once, so it never
 *   flashes empty when it first appears (e.g. when a streamed answer becomes a saved message).
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, immediate: Boolean = false) {
    Markdown(
        markdownState = rememberMarkdownState(text, retainState = true, immediate = immediate),
        modifier = modifier,
        animations = markdownAnimations(animateTextSize = { this }),
    )
}
