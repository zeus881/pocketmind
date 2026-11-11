package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.designsystem.component.rememberPmHaptics
import com.sanjay.pocketmind.core.designsystem.theme.PmSize
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.feature.chat.PhotoUi
import com.sanjay.pocketmind.feature.chat.VoiceUi
import com.sanjay.pocketmind.feature.chat.rememberPhotoPicker
import com.sanjay.pocketmind.feature.chat.resources.Res
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_add_photo
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_attached_photo
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_input_hint
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_prompt_library
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_remove_photo
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_send
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_stop
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_voice_start
import com.sanjay.pocketmind.feature.chat.resources.feature_chat_voice_stop
import org.jetbrains.compose.resources.stringResource

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
    voice: VoiceUi = VoiceUi(),
    onMicClick: () -> Unit = {},
    photo: PhotoUi = PhotoUi(),
    /** A content uri from the system photo picker. */
    onPhotoPicked: (String) -> Unit = {},
    onRemovePhoto: () -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val haptics = rememberPmHaptics()
    // What the recognizer heard replaces the box's text; from then on the user can edit it.
    LaunchedEffect(voice.transcriptVersion) { voice.transcript?.let { text = it } }
    // The system photo picker (no storage permission needed); null where photos can't be attached yet (iOS).
    val pickPhoto = rememberPhotoPicker(onPhotoPicked)
    Column(modifier = modifier.fillMaxWidth()) {
        if (photo.attached != null || photo.preparing) {
            AttachedPhoto(photo, onRemovePhoto, Modifier.padding(start = PmSpacing.l, top = PmSpacing.s))
        }
        InputRow(
            text = text,
            onTextChange = { text = it },
            isGenerating = isGenerating,
            prompts = prompts,
            voice = voice,
            onMicClick = onMicClick,
            showPhotoButton = photo.available && !isGenerating && pickPhoto != null,
            onAddPhoto = { pickPhoto?.invoke() },
            onSend = {
                haptics.confirm()
                onSend(text)
                text = ""
            },
            onStop = onStop,
        )
    }
}

@Composable
private fun AttachedPhoto(photo: PhotoUi, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val path = photo.attached
        if (path == null) {
            Box(Modifier.size(PmSize.photoChip), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            PhotoThumbnail(path, PmSize.photoChip, stringResource(Res.string.feature_chat_attached_photo))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.feature_chat_remove_photo))
            }
        }
    }
}

@Suppress("LongParameterList") // one row of controls; each callback is a distinct user action
@Composable
private fun InputRow(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    prompts: List<SavedPrompt>,
    voice: VoiceUi,
    onMicClick: () -> Unit,
    showPhotoButton: Boolean,
    onAddPhoto: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PmSpacing.m, vertical = PmSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prompts.isNotEmpty()) {
            PromptLibraryButton(prompts) { prompt ->
                // Insert at the end, so a half-typed message isn't lost.
                onTextChange(if (text.isBlank()) prompt.text else "${text.trimEnd()} ${prompt.text}")
            }
        }
        if (showPhotoButton) {
            IconButton(onClick = onAddPhoto) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = stringResource(Res.string.feature_chat_add_photo),
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(Res.string.feature_chat_input_hint)) },
            maxLines = 5,
            // A pill, like the input of most messaging apps (shape token: extra large, 32 dp).
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .padding(end = PmSpacing.s),
        )
        if (voice.available && !isGenerating) {
            IconButton(onClick = onMicClick) {
                Icon(
                    if (voice.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = stringResource(
                        if (voice.listening) {
                            Res.string.feature_chat_voice_stop
                        } else {
                            Res.string.feature_chat_voice_start
                        },
                    ),
                    tint = if (voice.listening) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
        }
        if (isGenerating) {
            FilledIconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(Res.string.feature_chat_stop))
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.feature_chat_send))
            }
        }
    }
}

@Composable
private fun PromptLibraryButton(prompts: List<SavedPrompt>, onPick: (SavedPrompt) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Bookmarks, contentDescription = stringResource(Res.string.feature_chat_prompt_library))
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
