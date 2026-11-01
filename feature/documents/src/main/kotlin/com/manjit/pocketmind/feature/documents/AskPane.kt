package com.manjit.pocketmind.feature.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manjit.pocketmind.core.designsystem.component.EmptyState
import com.manjit.pocketmind.core.designsystem.component.LoadingState
import com.manjit.pocketmind.data.documents.rag.GroundedPrompt
import com.manjit.pocketmind.data.documents.rag.IndexProgress
import com.manjit.pocketmind.data.documents.rag.IndexedDocument
import com.manjit.pocketmind.data.documents.rag.SourceChunk
import com.mikepenz.markdown.m3.Markdown

@Composable
fun AskPane(state: AskUiState, actions: AskActions) {
    when {
        state.isLoading -> LoadingState()

        !state.embeddingModelReady -> EmptyState(
            icon = Icons.Outlined.CloudDownload,
            title = stringResource(R.string.feature_documents_ask_need_model_title),
            body = stringResource(R.string.feature_documents_ask_need_model_body),
            actionLabel = stringResource(R.string.feature_documents_ask_get_model),
            onAction = actions.onOpenModels,
        )

        state.documents.isEmpty() && state.indexing == null -> EmptyState(
            icon = Icons.Outlined.QuestionAnswer,
            title = stringResource(R.string.feature_documents_ask_empty_title),
            body = stringResource(R.string.feature_documents_ask_empty_body),
            actionLabel = stringResource(R.string.feature_documents_add_pdf),
            onAction = actions.onAddPdf,
        )

        else -> AskContent(state, actions)
    }
}

@Composable
private fun AskContent(state: AskUiState, actions: AskActions) {
    var openSource by remember { mutableStateOf<SourceChunk?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.indexing?.let { IndexingCard(it) }
        DocumentList(state.documents, state.selectedId, actions)
        OutlinedButton(onClick = actions.onAddPdf, enabled = state.indexing == null) {
            Text(stringResource(R.string.feature_documents_add_pdf))
        }
        OutlinedTextField(
            value = state.question,
            onValueChange = actions.onQuestionChange,
            label = { Text(stringResource(R.string.feature_documents_ask_question)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isAnswering) {
            OutlinedButton(onClick = actions.onStop) { Text(stringResource(R.string.feature_documents_cancel)) }
        } else {
            Button(onClick = actions.onAsk, enabled = state.canAsk) {
                Text(stringResource(R.string.feature_documents_ask_action))
            }
        }
        state.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        state.answer?.let { answer -> AnswerCard(answer, state.sources, state.citedPages) { openSource = it } }
    }
    openSource?.let { source ->
        // Tapping a chip shows the exact passage: the user can verify the answer themselves.
        AlertDialog(
            onDismissRequest = { openSource = null },
            title = { Text(GroundedPrompt.pageLabel(source)) },
            text = { Text(source.text) },
            confirmButton = {
                TextButton(onClick = { openSource = null }) { Text(stringResource(R.string.feature_documents_close)) }
            },
        )
    }
}

@Composable
private fun IndexingCard(progress: IndexProgress) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = when (progress) {
                is IndexProgress.Embedding -> {
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    stringResource(R.string.feature_documents_indexing, progress.done, progress.total)
                }

                else -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    stringResource(R.string.feature_documents_reading_pdf)
                }
            }
            Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun DocumentList(documents: List<IndexedDocument>, selectedId: String?, actions: AskActions) {
    Column(Modifier.selectableGroup()) {
        documents.forEach { doc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = doc.id == selectedId, role = Role.RadioButton) {
                        actions.onSelect(doc.id)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = doc.id == selectedId, onClick = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(doc.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pluralStringResource(R.plurals.feature_documents_pages, doc.pageCount, doc.pageCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { actions.onDelete(doc.id) }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.feature_documents_delete, doc.name))
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(
    answer: String,
    sources: List<SourceChunk>,
    citedPages: Set<Int>,
    onOpenSource: (SourceChunk) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (answer.isBlank()) {
                Text(stringResource(R.string.feature_documents_searching))
            } else {
                Markdown(content = answer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (sources.isNotEmpty()) {
                Text(stringResource(R.string.feature_documents_sources), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.sortedBy { it.startPage }.forEach { source ->
                        val cited = (source.startPage..source.endPage).any { it in citedPages }
                        FilterChip(
                            selected = cited,
                            onClick = { onOpenSource(source) },
                            label = { Text(GroundedPrompt.pageLabel(source)) },
                            leadingIcon = if (cited) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
