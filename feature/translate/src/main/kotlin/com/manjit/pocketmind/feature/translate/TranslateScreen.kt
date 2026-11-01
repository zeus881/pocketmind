package com.manjit.pocketmind.feature.translate

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Translate screen. Its real content is built in a later phase. */
@Composable
fun TranslateScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Translate,
        title = stringResource(R.string.feature_translate_title),
        modifier = modifier,
    )
}
