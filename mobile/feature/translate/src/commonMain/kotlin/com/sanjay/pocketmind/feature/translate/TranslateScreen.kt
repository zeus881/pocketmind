package com.sanjay.pocketmind.feature.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.platform.languageDisplayName
import com.sanjay.pocketmind.core.designsystem.platform.plainTextClipEntry
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.data.documents.Language
import com.sanjay.pocketmind.data.documents.Languages
import com.sanjay.pocketmind.feature.translate.resources.Res
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_action
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_copy
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_error
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_error_no_model
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_from
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_input
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_stop
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_swap
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_title
import com.sanjay.pocketmind.feature.translate.resources.feature_translate_to
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TranslateScreen(viewModel: TranslateViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TranslateContent(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranslateContent(state: TranslateUiState, vm: TranslateActions) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Res.string.feature_translate_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Centred at a readable width on tablets and foldables.
                .readableWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PmSpacing.l),
            verticalArrangement = Arrangement.spacedBy(PmSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguagePicker(Res.string.feature_translate_from, state.from, vm::setFrom, Modifier.weight(1f))
                IconButton(onClick = vm::swap) {
                    Icon(Icons.Outlined.SwapHoriz, stringResource(Res.string.feature_translate_swap))
                }
                LanguagePicker(Res.string.feature_translate_to, state.to, vm::setTo, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChange,
                label = { Text(stringResource(Res.string.feature_translate_input)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isTranslating) {
                OutlinedButton(onClick = vm::stop) { Text(stringResource(Res.string.feature_translate_stop)) }
            } else {
                Button(onClick = vm::translate, enabled = state.canTranslate) {
                    Text(stringResource(Res.string.feature_translate_action))
                }
            }
            state.error?.let { kind ->
                Text(
                    stringResource(kind.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            if (state.output.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PmSpacing.l)) {
                        // SelectionContainer: the user can long-press to select part of the text.
                        SelectionContainer {
                            Text(
                                state.output,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(plainTextClipEntry("translation", state.output))
                            }
                        }) { Icon(Icons.Outlined.ContentCopy, stringResource(Res.string.feature_translate_copy)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    label: StringResource,
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Languages.all.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName()) },
                    onClick = {
                        onSelect(language)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Language name in the user's own UI language ("Hindi", or "हिन्दी" when the app is in Hindi). */
private fun Language.displayName(): String = languageDisplayName(code)

private fun ErrorKind.messageRes(): StringResource = when (this) {
    ErrorKind.NO_MODEL -> Res.string.feature_translate_error_no_model
    else -> Res.string.feature_translate_error
}
