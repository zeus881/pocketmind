package com.sanjay.pocketmind.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sanjay.pocketmind.app.resources.Res
import com.sanjay.pocketmind.app.resources.about_disclaimer_body
import com.sanjay.pocketmind.app.resources.about_disclaimer_title
import com.sanjay.pocketmind.app.resources.about_privacy_body
import com.sanjay.pocketmind.app.resources.about_privacy_title
import com.sanjay.pocketmind.app.resources.about_what_body
import com.sanjay.pocketmind.app.resources.about_what_title
import com.sanjay.pocketmind.app.resources.app_name
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Root of the shared (Compose Multiplatform) UI — what the iOS app shows.
 *
 * V3 phase 1 builds the cross-platform pipeline end to end (shared Kotlin → iOS framework → Xcode app),
 * so this first shared screen explains PocketMind with v2's real onboarding text (English, Hindi,
 * Punjabi). Phase 2c moves the v2 screens here, and phase 3 replaces this theme with the V3 design system.
 */
@Composable
fun App() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            AboutScreen()
        }
    }
}

/** What PocketMind is, its privacy promise and the "answers can be wrong" disclaimer. */
@Composable
internal fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Keeps text out of the notch, the home indicator and the status bar on every phone.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(AppTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppTokens.sectionGap),
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            // Screen readers (VoiceOver, TalkBack) let users jump between headings.
            modifier = Modifier.semantics { heading() },
        )
        AboutSection(Res.string.about_what_title, Res.string.about_what_body)
        AboutSection(Res.string.about_privacy_title, Res.string.about_privacy_body)
        AboutSection(Res.string.about_disclaimer_title, Res.string.about_disclaimer_body)
    }
}

@Composable
private fun AboutSection(title: StringResource, body: StringResource) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.titleGap)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(body), style = MaterialTheme.typography.bodyLarge)
    }
}
