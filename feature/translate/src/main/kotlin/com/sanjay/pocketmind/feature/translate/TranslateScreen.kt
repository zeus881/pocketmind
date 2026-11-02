package com.sanjay.pocketmind.feature.translate

import android.content.ClipData
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.data.documents.Language
import com.sanjay.pocketmind.data.documents.Languages
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TranslateScreen(viewModel: TranslateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TranslateContent(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateContent(state: TranslateUiState, vm: TranslateViewModel) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_translate_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PmSpacing.l),
            verticalArrangement = Arrangement.spacedBy(PmSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguagePicker(R.string.feature_translate_from, state.from, vm::setFrom, Modifier.weight(1f))
                IconButton(onClick = vm::swap) {
                    Icon(Icons.Outlined.SwapHoriz, stringResource(R.string.feature_translate_swap))
                }
                LanguagePicker(R.string.feature_translate_to, state.to, vm::setTo, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChange,
                label = { Text(stringResource(R.string.feature_translate_input)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isTranslating) {
                OutlinedButton(onClick = vm::stop) { Text(stringResource(R.string.feature_translate_stop)) }
            } else {
                Button(onClick = vm::translate, enabled = state.canTranslate) {
                    Text(stringResource(R.string.feature_translate_action))
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
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("translation", state.output)))
                            }
                        }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.feature_translate_copy)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(labelRes: Int, selected: Language, onSelect: (Language) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(labelRes)) },
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
private fun Language.displayName(): String = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.NO_MODEL -> R.string.feature_translate_error_no_model
    else -> R.string.feature_translate_error
}
