package com.sanjay.pocketmind.feature.documents

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.EmptyState
import com.sanjay.pocketmind.core.designsystem.component.ErrorState
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.data.documents.SummaryProgress
import kotlinx.coroutines.launch

/** The "Summarize" part of the documents screen: one composable per UI state. */
@Composable
fun SummarizePane(state: SummarizeUiState, onPickPdf: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    when (state) {
        SummarizeUiState.Idle -> EmptyState(
            icon = Icons.Outlined.Description,
            title = stringResource(R.string.feature_documents_summarize_empty_title),
            body = stringResource(R.string.feature_documents_summarize_empty_body),
            actionLabel = stringResource(R.string.feature_documents_choose_pdf),
            onAction = onPickPdf,
        )

        SummarizeUiState.ReadingPdf -> LoadingState(message = stringResource(R.string.feature_documents_reading_pdf))

        is SummarizeUiState.Summarizing -> SummarizingContent(state, onCancel)

        is SummarizeUiState.Done -> SummaryContent(state, onPickPdf)

        is SummarizeUiState.Error -> ErrorState(stringResource(state.kind.messageRes()), onRetry)
    }
}

@Composable
private fun SummarizingContent(state: SummarizeUiState.Summarizing, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(state.documentName, style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.feature_documents_pages, state.pageCount, state.pageCount))
        val progress = state.progress
        val label = when (progress) {
            is SummaryProgress.Mapping ->
                stringResource(R.string.feature_documents_part_x_of_y, progress.part, progress.total)

            is SummaryProgress.Reducing -> stringResource(R.string.feature_documents_combining)

            else -> stringResource(R.string.feature_documents_preparing)
        }
        if (progress is SummaryProgress.Mapping) {
            LinearProgressIndicator(progress = { progress.part.toFloat() / progress.total }, Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.feature_documents_cancel)) }
    }
}

@Composable
private fun SummaryContent(state: SummarizeUiState.Done, onPickPdf: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.documentName, style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(R.plurals.feature_documents_pages, state.pageCount, state.pageCount) + " · " +
                pluralStringResource(R.plurals.feature_documents_parts, state.parts, state.parts),
            style = MaterialTheme.typography.bodySmall,
        )
        Card(Modifier.fillMaxWidth()) {
            StableMarkdown(state.summary, modifier = Modifier.padding(16.dp))
        }
        Row {
            IconButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("summary", state.summary))) }
            }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.feature_documents_copy)) }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, state.summary)
                context.startActivity(Intent.createChooser(send, null))
            }) { Icon(Icons.Outlined.Share, stringResource(R.string.feature_documents_share)) }
        }
        Text(
            stringResource(R.string.feature_documents_check_facts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPickPdf) { Text(stringResource(R.string.feature_documents_choose_another)) }
    }
}

internal fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.NO_TEXT_IN_DOCUMENT -> R.string.feature_documents_error_no_text
    ErrorKind.FILE_UNREADABLE -> R.string.feature_documents_error_unreadable
    ErrorKind.NO_MODEL -> R.string.feature_documents_error_no_model
    else -> R.string.feature_documents_error_generic
}
