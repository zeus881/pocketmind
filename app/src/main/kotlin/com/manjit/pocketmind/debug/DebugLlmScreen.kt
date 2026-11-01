package com.manjit.pocketmind.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manjit.pocketmind.R
import com.manjit.pocketmind.core.llm.EngineState
import com.manjit.pocketmind.core.llm.FailureReason

/**
 * TEMPORARY: one-prompt playground used to verify the engine on a real phone.
 * `hiltViewModel()` asks Hilt for the ViewModel, scoped to this navigation entry.
 * `collectAsStateWithLifecycle()` stops collecting when the app is in the background.
 */
@Composable
fun DebugLlmScreen(viewModel: DebugLlmViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("What is the capital of France?") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.titleLarge)
        Text(engineLabel(state.engine), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = viewModel::load, enabled = state.engine !is EngineState.Loading) {
            Text(stringResource(R.string.debug_load))
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.debug_prompt)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.send(prompt) },
                enabled = state.engine is EngineState.Ready && !state.generation.isGenerating,
            ) { Text(stringResource(R.string.debug_send)) }
            OutlinedButton(onClick = viewModel::stop, enabled = state.generation.isGenerating) {
                Text(stringResource(R.string.debug_stop))
            }
        }
        state.generation.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(state.generation.output, style = MaterialTheme.typography.bodyLarge)
        state.generation.stats?.let { stats ->
            Text(
                stringResource(
                    R.string.debug_stats,
                    stats.decodeTokensPerSecond,
                    stats.backend.label,
                    stats.timeToFirstTokenMs,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun engineLabel(state: EngineState): String = when (state) {
    EngineState.Idle -> stringResource(R.string.debug_engine_idle)

    is EngineState.Loading -> stringResource(R.string.debug_engine_loading)

    is EngineState.Ready -> stringResource(R.string.debug_engine_ready, state.backend.label, state.loadMs)

    is EngineState.Failed -> when (state.reason) {
        FailureReason.FILE_NOT_FOUND -> stringResource(R.string.debug_engine_no_file, state.modelPath)
        FailureReason.INIT_FAILED -> stringResource(R.string.debug_engine_failed)
    }
}
