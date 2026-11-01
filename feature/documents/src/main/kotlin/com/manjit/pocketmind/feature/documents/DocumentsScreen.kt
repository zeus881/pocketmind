package com.manjit.pocketmind.feature.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Documents tab. The PDF is chosen with the SYSTEM file picker (Storage Access Framework):
 * `OpenDocument` returns a Uri to the one file the user picked, so the app needs no storage
 * permission at all and can't see any other file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(viewModel: SummarizeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onPdfPicked) // null = the user backed out of the picker
    }
    val pickPdf = { picker.launch(arrayOf("application/pdf")) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_documents_title)) }) }) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SummarizePane(state, onPickPdf = pickPdf, onCancel = viewModel::cancel, onRetry = viewModel::retry)
        }
    }
}
