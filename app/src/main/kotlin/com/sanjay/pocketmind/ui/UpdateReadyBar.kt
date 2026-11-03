package com.sanjay.pocketmind.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.sanjay.pocketmind.R
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing

/**
 * "Update ready — Restart / Later", above everything (and above the navigation bar). Restarting
 * is the user's choice: an answer being written would be lost, so it never happens by itself.
 */
@Composable
fun UpdateReadyBar(onRestart: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Snackbar(
        modifier = modifier
            .navigationBarsPadding()
            .padding(PmSpacing.l)
            .semantics { liveRegion = LiveRegionMode.Polite },
        action = {
            Row {
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                TextButton(onClick = onRestart) { Text(stringResource(R.string.update_restart)) }
            }
        },
    ) {
        Text(stringResource(R.string.update_ready))
    }
}
