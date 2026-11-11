package com.sanjay.pocketmind.feature.documents

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.platform.rememberPdfPicker
import com.sanjay.pocketmind.feature.documents.resources.Res
import com.sanjay.pocketmind.feature.documents.resources.feature_documents_tab_ask
import com.sanjay.pocketmind.feature.documents.resources.feature_documents_tab_summarize
import com.sanjay.pocketmind.feature.documents.resources.feature_documents_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    /** A PDF shared from another app: summarized once when the screen opens. */
    sharedPdfUri: String? = null,
    summarizeViewModel: SummarizeViewModel = koinViewModel(),
    askViewModel: AskViewModel = koinViewModel(),
) {
    val summarizeState by summarizeViewModel.uiState.collectAsStateWithLifecycle()
    val askState by askViewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Saveable: a rotation must not summarize the shared PDF a second time.
    var sharedHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sharedPdfUri) {
        if (sharedPdfUri != null && !sharedHandled) {
            sharedHandled = true
            summarizeViewModel.onPdfPicked(sharedPdfUri)
        }
    }

    // Coming back from the model screen: the embedding model may now be downloaded.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { askViewModel.refreshModelState() }

    // The system picker for one PDF (Android: Storage Access Framework; iOS: document picker).
    val summarizePicker = rememberPdfPicker(summarizeViewModel::onPdfPicked)
    val askPicker = rememberPdfPicker(askViewModel::addPdf)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Res.string.feature_documents_title)) }) }) { padding ->
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
                    text = { Text(stringResource(Res.string.feature_documents_tab_summarize)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(Res.string.feature_documents_tab_ask)) },
                )
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    SummarizePane(
                        state = summarizeState,
                        onPickPdf = summarizePicker,
                        onCancel = summarizeViewModel::cancel,
                        onRetry = summarizeViewModel::retry,
                    )
                } else {
                    AskPane(
                        state = askState,
                        actions = AskActions(
                            onAddPdf = askPicker,
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
