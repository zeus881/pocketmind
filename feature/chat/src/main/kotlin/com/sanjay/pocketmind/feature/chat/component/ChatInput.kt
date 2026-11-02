package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.designsystem.component.rememberPmHaptics
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.feature.chat.R

/**
 * Text field + one button that is "Send" when idle and "Stop" while an answer streams.
 * The typed text is `rememberSaveable`, so it survives screen rotation.
 *
 * Always usable, even while the model is still loading: a message sent then simply waits
 * for the load to finish (the "thinking" bubble shows at once), instead of a dead input box
 * for 5–13 seconds (AUDIT M-4).
 */
@Composable
internal fun ChatInput(
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** Pre-filled text (shared from another app); the user still decides to send it. */
    initialText: String = "",
    /** The prompt library: a button inserts one of them into the box. */
    prompts: List<SavedPrompt> = emptyList(),
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val haptics = rememberPmHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PmSpacing.m, vertical = PmSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prompts.isNotEmpty()) {
            PromptLibraryButton(prompts) { prompt ->
                // Insert at the end, so a half-typed message isn't lost.
                text = if (text.isBlank()) prompt.text else "${text.trimEnd()} ${prompt.text}"
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.feature_chat_input_hint)) },
            maxLines = 5,
            // A pill, like the input of most messaging apps (shape token: extra large, 32 dp).
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .padding(end = PmSpacing.s),
        )
        if (isGenerating) {
            FilledIconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.feature_chat_stop))
            }
        } else {
            FilledIconButton(
                onClick = {
                    haptics.confirm()
                    onSend(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.feature_chat_send))
            }
        }
    }
}

@Composable
private fun PromptLibraryButton(prompts: List<SavedPrompt>, onPick: (SavedPrompt) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Bookmarks, contentDescription = stringResource(R.string.feature_chat_prompt_library))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            prompts.forEach { prompt ->
                DropdownMenuItem(
                    text = { Text(prompt.title) },
                    onClick = {
                        open = false
                        onPick(prompt)
                    },
                )
            }
        }
    }
}
