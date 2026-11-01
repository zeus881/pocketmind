package com.manjit.pocketmind.feature.documents

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Documents screen. Its real content is built in a later phase. */
@Composable
fun DocumentsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Description,
        title = stringResource(R.string.feature_documents_title),
        modifier = modifier,
    )
}
