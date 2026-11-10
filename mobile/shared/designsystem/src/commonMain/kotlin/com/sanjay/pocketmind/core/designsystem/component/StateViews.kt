package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sanjay.pocketmind.core.designsystem.resources.Res
import com.sanjay.pocketmind.core.designsystem.resources.core_designsystem_retry
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import org.jetbrains.compose.resources.stringResource

/*
 * CLAUDE.md requires every screen to have loading, empty, error and content states.
 * These three shared composables give all screens the same look for the first three, so a
 * feature module only has to write its "content" state.
 */

/** Full-screen spinner with an optional message, e.g. "Loading model…". */
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PmSpacing.xl)
            // liveRegion makes TalkBack announce the message when it appears or changes.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(PmSpacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

/** Shown when there is simply nothing to display yet (no chats, no results...). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PmSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PmSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decorative: the title right below says the same thing, so TalkBack skips it.
        PmIllustration(icon)
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * A readable error message plus a Retry button (CLAUDE.md: errors must offer retry), and an
 * optional second, less prominent action (e.g. "Open models") when retrying alone can't help.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PmSpacing.xl)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(PmSpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PmIllustration(Icons.Outlined.ErrorOutline, tone = IllustrationTone.Error)
        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text(stringResource(Res.string.core_designsystem_retry)) }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            OutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
        }
    }
}
