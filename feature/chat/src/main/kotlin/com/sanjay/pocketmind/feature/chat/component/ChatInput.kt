package com.sanjay.pocketmind.feature.chat.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.designsystem.component.rememberPmHaptics
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
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
) {
    var text by rememberSaveable { mutableStateOf("") }
    val haptics = rememberPmHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PmSpacing.m, vertical = PmSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.feature_chat_input_hint)) },
            maxLines = 5,
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
