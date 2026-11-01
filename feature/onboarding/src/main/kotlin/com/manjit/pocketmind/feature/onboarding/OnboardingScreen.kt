package com.manjit.pocketmind.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manjit.pocketmind.core.designsystem.component.EmptyState

/** Phase 1 shell for the Welcome screen. Its real content is built in a later phase. */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.WavingHand,
        title = stringResource(R.string.feature_onboarding_title),
        modifier = modifier,
    )
}
