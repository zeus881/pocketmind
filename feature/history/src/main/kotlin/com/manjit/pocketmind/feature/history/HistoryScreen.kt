package com.manjit.pocketmind.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the History screen. Its real content is built in a later phase. */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.History,
        title = stringResource(R.string.feature_history_title),
        modifier = modifier,
    )
}
