package com.sanjay.pocketmind.feature.benchmark

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.EmptyState
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.BenchmarkResult
import com.sanjay.pocketmind.data.benchmark.BenchmarkProgress
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BenchmarkScreen(onBack: () -> Unit, viewModel: BenchmarkViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BenchmarkEvent.Share -> {
                    // Android's share sheet: the user chooses where the text goes. We send nothing ourselves.
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, event.text)
                    val title = resources.getString(R.string.feature_benchmark_share)
                    context.startActivity(Intent.createChooser(send, title))
                }

                is BenchmarkEvent.Error -> snackbar.showSnackbar(
                    resources.getString(
                        if (event.kind == ErrorKind.NO_MODEL) {
                            R.string.feature_benchmark_error_no_model
                        } else {
                            R.string.feature_benchmark_error
                        },
                    ),
                )
            }
        }
    }

    BenchmarkContent(
        state = state,
        onRun = viewModel::start,
        onCancel = viewModel::cancel,
        onShare = viewModel::share,
        onDelete = viewModel::delete,
        onBack = onBack,
        snackbarHostState = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkContent(
    state: BenchmarkUiState,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onShare: (BenchmarkResult) -> Unit,
    onDelete: (BenchmarkResult) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_benchmark_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.feature_benchmark_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Centred at a readable width on tablets and foldables.
                .readableWidth(),
            contentPadding = PaddingValues(PmSpacing.l),
            verticalArrangement = Arrangement.spacedBy(PmSpacing.m),
        ) {
            item { RunCard(state, onRun, onCancel) }
            when {
                state.isLoadingHistory -> item { LoadingState(Modifier.padding(PmSpacing.xxl)) }

                state.runs.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.Speed,
                        title = stringResource(R.string.feature_benchmark_empty_title),
                        body = stringResource(R.string.feature_benchmark_empty_body),
                        modifier = Modifier.padding(top = PmSpacing.xxl),
                    )
                }

                else -> items(state.runs, key = { it.id }) { run -> ResultCard(run, onShare, onDelete) }
            }
        }
    }
}

@Composable
private fun RunCard(state: BenchmarkUiState, onRun: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PmSpacing.l), verticalArrangement = Arrangement.spacedBy(PmSpacing.s)) {
            Text(stringResource(R.string.feature_benchmark_intro), style = MaterialTheme.typography.bodyMedium)
            val progress = state.progress
            if (progress == null) {
                Button(onClick = onRun) { Text(stringResource(R.string.feature_benchmark_run)) }
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                val label = when (progress) {
                    is BenchmarkProgress.RunningPrompt -> stringResource(
                        R.string.feature_benchmark_running_prompt,
                        progress.index,
                        progress.total,
                        progress.name,
                    )

                    else -> stringResource(R.string.feature_benchmark_loading)
                }
                Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.feature_benchmark_cancel)) }
            }
        }
    }
}

@Composable
private fun ResultCard(run: BenchmarkResult, onShare: (BenchmarkResult) -> Unit, onDelete: (BenchmarkResult) -> Unit) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.createdAt))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PmSpacing.l), verticalArrangement = Arrangement.spacedBy(PmSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${run.modelName} · ${run.backend}", style = MaterialTheme.typography.titleMedium)
                    Text(date, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onShare(run) }) {
                    Icon(Icons.Outlined.Share, stringResource(R.string.feature_benchmark_share))
                }
                IconButton(onClick = { onDelete(run) }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.feature_benchmark_delete))
                }
            }
            val speed = String.format(Locale.US, "%.1f tok/s", run.averageDecodeTokensPerSecond)
            val ttft = String.format(Locale.US, "%.2f s", run.averageTimeToFirstTokenMs / MS)
            Metric(R.string.feature_benchmark_metric_speed, speed)
            Metric(R.string.feature_benchmark_metric_ttft, ttft)
            Metric(R.string.feature_benchmark_metric_load, String.format(Locale.US, "%.1f s", run.loadMs / MS))
            Metric(R.string.feature_benchmark_metric_memory, "${run.peakMemoryMb} MB")
            Text(
                run.device,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(labelRes: Int, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private const val MS = 1000.0
