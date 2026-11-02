package com.sanjay.pocketmind.feature.documents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Markdown that does not flicker while an answer streams in.
 *
 * `retainState = true` keeps the previous rendering on screen until the new text has been
 * parsed (plain `Markdown(content = …)` shows an empty frame on every update), and the
 * default height animation is switched off so the card does not wobble on every token.
 * Same idea as the chat bubble; feature modules may not share code with each other, so
 * this small helper lives here too.
 */
@Composable
internal fun StableMarkdown(text: String, modifier: Modifier = Modifier) {
    Markdown(
        markdownState = rememberMarkdownState(text, retainState = true),
        modifier = modifier,
        animations = markdownAnimations(animateTextSize = { this }),
    )
}
