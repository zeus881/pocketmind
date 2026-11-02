package com.sanjay.pocketmind.feature.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.designsystem.component.readableWidth

/**
 * Documents tab with two tools: "Summarize" (phase 6) and "Ask" (on-device RAG, phase 7).
 *
 * PDFs are chosen with the SYSTEM file picker (Storage Access Framework): `OpenDocument`
 * returns a Uri to the one file the user picked, so the app needs no storage permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onOpenModels: () -> Unit,
    summarizeViewModel: SummarizeViewModel = hiltViewModel(),
    askViewModel: AskViewModel = hiltViewModel(),
) {
    val summarizeState by summarizeViewModel.uiState.collectAsStateWithLifecycle()
    val askState by askViewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Coming back from the model screen: the embedding model may now be downloaded.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { askViewModel.refreshModelState() }

    // null = the user backed out of the picker.
    val summarizePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(summarizeViewModel::onPdfPicked)
    }
    val askPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(askViewModel::addPdf)
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_documents_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Centred at a readable width on tablets and foldables.
                .readableWidth(),
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.feature_documents_tab_summarize)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.feature_documents_tab_ask)) },
                )
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    SummarizePane(
                        state = summarizeState,
                        onPickPdf = { summarizePicker.launch(PDF_MIME) },
                        onCancel = summarizeViewModel::cancel,
                        onRetry = summarizeViewModel::retry,
                    )
                } else {
                    AskPane(
                        state = askState,
                        actions = AskActions(
                            onAddPdf = { askPicker.launch(PDF_MIME) },
                            onSelect = askViewModel::select,
                            onDelete = askViewModel::delete,
                            onQuestionChange = askViewModel::onQuestionChange,
                            onAsk = askViewModel::ask,
                            onStop = askViewModel::stop,
                            onOpenModels = onOpenModels,
                        ),
                    )
                }
            }
        }
    }
}

private val PDF_MIME = arrayOf("application/pdf")
