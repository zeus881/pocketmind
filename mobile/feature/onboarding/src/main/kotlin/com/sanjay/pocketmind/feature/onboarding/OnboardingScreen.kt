package com.sanjay.pocketmind.feature.onboarding

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.designsystem.theme.PmSize
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private const val PAGE_COUNT = 4

/** First-launch introduction. [onFinished] is called after "Get started" is saved. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finished.collect { onFinished() } }
    OnboardingContent(state = state, onGetStarted = viewModel::finish)
}

@Composable
fun OnboardingContent(state: OnboardingUiState, onGetStarted: () -> Unit) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val modelLine = if (state.recommendedName != null) {
        stringResource(
            R.string.feature_onboarding_model_body,
            state.deviceRamGb,
            state.recommendedName,
            Formatter.formatShortFileSize(context, state.recommendedSizeBytes),
        )
    } else {
        stringResource(R.string.feature_onboarding_model_body_generic)
    }
    val what = stringResource(R.string.feature_onboarding_what_body)
    val privacy = stringResource(R.string.feature_onboarding_privacy_body)
    val disclaimer = stringResource(R.string.feature_onboarding_disclaimer_body)
    val pages = listOf(
        Page(Icons.Outlined.PhoneAndroid, R.string.feature_onboarding_what_title, what),
        Page(Icons.Outlined.Lock, R.string.feature_onboarding_privacy_title, privacy),
        Page(Icons.Outlined.CloudDownload, R.string.feature_onboarding_model_title, modelLine),
        Page(Icons.Outlined.WarningAmber, R.string.feature_onboarding_disclaimer_title, disclaimer),
    )

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(PmSpacing.xl),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index -> PageContent(pages[index]) }
        PageDots(current = pager.currentPage)
        Spacer(Modifier.height(PmSpacing.l))
        val isLast = pager.currentPage == PAGE_COUNT - 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (!isLast) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGE_COUNT - 1) } }) {
                    Text(stringResource(R.string.feature_onboarding_skip))
                }
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(R.string.feature_onboarding_next))
                }
            } else {
                // The disclaimer page has the only "finish" button, so every user sees it.
                Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feature_onboarding_get_started))
                }
            }
        }
    }
}

private data class Page(val icon: ImageVector, val titleRes: Int, val body: String)

@Composable
private fun PageContent(page: Page) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()), // large fonts must never cut text off
        verticalArrangement = Arrangement.spacedBy(PmSpacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null, // decorative: the title says the same
            modifier = Modifier.size(PmSize.heroIcon),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PageDots(current: Int) {
    val description = stringResource(R.string.feature_onboarding_page_of, current + 1, PAGE_COUNT)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(PAGE_COUNT) { index ->
            val colors = MaterialTheme.colorScheme
            val color = if (index == current) colors.primary else colors.outlineVariant
            Box(
                Modifier
                    .padding(PmSpacing.xs)
                    .size(PmSpacing.s)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
