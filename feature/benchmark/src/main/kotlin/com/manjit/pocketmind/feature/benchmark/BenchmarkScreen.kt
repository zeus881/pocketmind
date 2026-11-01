package com.manjit.pocketmind.feature.benchmark

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Benchmark screen. Its real content is built in a later phase. */
@Composable
fun BenchmarkScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Speed,
        title = stringResource(R.string.feature_benchmark_title),
        modifier = modifier,
    )
}
