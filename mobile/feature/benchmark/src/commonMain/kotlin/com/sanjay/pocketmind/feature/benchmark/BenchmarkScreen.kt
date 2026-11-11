package com.sanjay.pocketmind.feature.benchmark

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.format.formatFixed
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.EmptyState
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.platform.formatDateTime
import com.sanjay.pocketmind.core.designsystem.platform.rememberTextSharer
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.BenchmarkResult
import com.sanjay.pocketmind.data.benchmark.BenchmarkProgress
import com.sanjay.pocketmind.feature.benchmark.resources.Res
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_back
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_cancel
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_delete
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_empty_body
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_empty_title
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_error
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_error_no_model
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_intro
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_loading
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_metric_load
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_metric_memory
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_metric_speed
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_metric_ttft
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_run
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_running_prompt
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_share
import com.sanjay.pocketmind.feature.benchmark.resources.feature_benchmark_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BenchmarkScreen(onBack: () -> Unit, viewModel: BenchmarkViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val share = rememberTextSharer()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BenchmarkEvent.Share -> {
                    // The platform share sheet: the user chooses where the text goes. We send nothing ourselves.
                    share(event.text, getString(Res.string.feature_benchmark_share))
                }

                is BenchmarkEvent.Error -> snackbar.showSnackbar(
                    getString(
                        if (event.kind == ErrorKind.NO_MODEL) {
                            Res.string.feature_benchmark_error_no_model
                        } else {
                            Res.string.feature_benchmark_error
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
                title = { Text(stringResource(Res.string.feature_benchmark_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.feature_benchmark_back))
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
                        title = stringResource(Res.string.feature_benchmark_empty_title),
                        body = stringResource(Res.string.feature_benchmark_empty_body),
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
            Text(stringResource(Res.string.feature_benchmark_intro), style = MaterialTheme.typography.bodyMedium)
            val progress = state.progress
            if (progress == null) {
                Button(onClick = onRun) { Text(stringResource(Res.string.feature_benchmark_run)) }
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                val label = when (progress) {
                    is BenchmarkProgress.RunningPrompt -> stringResource(
                        Res.string.feature_benchmark_running_prompt,
                        progress.index,
                        progress.total,
                        progress.name,
                    )

                    else -> stringResource(Res.string.feature_benchmark_loading)
                }
                Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.feature_benchmark_cancel)) }
            }
        }
    }
}

@Composable
private fun ResultCard(run: BenchmarkResult, onShare: (BenchmarkResult) -> Unit, onDelete: (BenchmarkResult) -> Unit) {
    val date = formatDateTime(run.createdAt)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PmSpacing.l), verticalArrangement = Arrangement.spacedBy(PmSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${run.modelName} · ${run.backend}", style = MaterialTheme.typography.titleMedium)
                    Text(date, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onShare(run) }) {
                    Icon(Icons.Outlined.Share, stringResource(Res.string.feature_benchmark_share))
                }
                IconButton(onClick = { onDelete(run) }) {
                    Icon(Icons.Outlined.Delete, stringResource(Res.string.feature_benchmark_delete))
                }
            }
            // Fixed "." decimals on every phone, like v2's Locale.US: these numbers are compared across devices.
            val speed = "${run.averageDecodeTokensPerSecond.formatFixed(1)} tok/s"
            val ttft = "${(run.averageTimeToFirstTokenMs / MS).formatFixed(2)} s"
            Metric(Res.string.feature_benchmark_metric_speed, speed)
            Metric(Res.string.feature_benchmark_metric_ttft, ttft)
            Metric(Res.string.feature_benchmark_metric_load, "${(run.loadMs / MS).formatFixed(1)} s")
            Metric(Res.string.feature_benchmark_metric_memory, "${run.peakMemoryMb} MB")
            Text(
                run.device,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: StringResource, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private const val MS = 1000.0
