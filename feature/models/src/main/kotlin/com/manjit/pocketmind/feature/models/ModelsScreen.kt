package com.manjit.pocketmind.feature.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Models screen. Its real content is built in a later phase. */
@Composable
fun ModelsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Memory,
        title = stringResource(R.string.feature_models_title),
        modifier = modifier,
    )
}
