package com.manjit.pocketmind.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Settings screen. Its real content is built in a later phase. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Settings,
        title = stringResource(R.string.feature_settings_title),
        modifier = modifier,
    )
}
