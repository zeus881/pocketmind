package com.sanjay.pocketmind

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.designsystem.theme.PocketMindTheme
import com.sanjay.pocketmind.feature.chat.navigation.ChatRoute
import com.sanjay.pocketmind.feature.onboarding.navigation.OnboardingRoute
import com.sanjay.pocketmind.ui.LockScreen
import com.sanjay.pocketmind.ui.PocketMindAppUi
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only Activity. With Compose + Navigation we use a "single-activity" app: screens are
 * composables swapped by the NavHost, not separate Activities.
 *
 * It extends FragmentActivity (instead of ComponentActivity) only because BiometricPrompt
 * needs it for the optional app lock.
 *
 * App lock design (v2): the app UI is ALWAYS composed and the lock screen is drawn ON TOP of
 * it. In v1 the lock replaced the app UI, which threw away the navigation back stack and every
 * screen's ViewModel, so an answer being generated or a PDF being summarized was lost. While
 * locked, the content underneath is hidden from TalkBack and can't be touched.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; Scaffold adds the right insets.
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val current = state) {
                MainUiState.Loading -> PocketMindTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                }

                is MainUiState.Ready -> PocketMindTheme(
                    themeMode = current.settings.themeMode,
                    dynamicColor = current.settings.dynamicColor,
                ) {
                    // The start screen is decided ONCE; later settings changes must not reset navigation.
                    val start = remember { if (current.settings.onboardingDone) ChatRoute() else OnboardingRoute }
                    HideRecentsPreview(current.settings.appLockEnabled)
                    LockedAppFrame(locked = current.locked) {
                        PocketMindAppUi(startDestination = start)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        super.onStop()
        // A rotation recreates the activity; it isn't "leaving the app".
        if (!isChangingConfigurations) viewModel.onAppBackground(SystemClock.elapsedRealtime())
    }

    /**
     * Every `rememberLauncherForActivityResult(...).launch()` in the app (PDF picker, photo picker)
     * ends up here. We tell the lock timer that the next trip to the background is ours, so the
     * picker doesn't trigger the lock and its result isn't lost.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        viewModel.onLaunchingForResult()
        super.startActivityForResult(intent, requestCode, options)
    }

    /**
     * Draws [content] and, when [locked], the lock screen above it. The content stays composed
     * (state survives) but is removed from the accessibility tree, and the keyboard is closed.
     */
    @Composable
    private fun LockedAppFrame(locked: Boolean, content: @Composable () -> Unit) {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        LaunchedEffect(locked) {
            if (locked) {
                keyboard?.hide()
                focus.clearFocus(force = true)
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(if (locked) Modifier.clearAndSetSemantics { } else Modifier) { content() }
            // LockScreen is an opaque Surface: it covers the content and blocks touches to it.
            if (locked) LockScreen(onUnlock = ::authenticate)
        }
    }

    /**
     * With the app lock on, the Recents screen must not show a picture of the last chat.
     * Android 13+ can hide just that thumbnail, without also blocking the user's own screenshots
     * (which FLAG_SECURE would do). Older versions have no such switch.
     */
    @Composable
    private fun HideRecentsPreview(lockEnabled: Boolean) {
        LaunchedEffect(lockEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!lockEnabled)
        }
    }

    /** Shows the system fingerprint/face/PIN dialog. The OS does the check; we only get the result. */
    private fun authenticate() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlock()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_subtitle))
            // Fingerprint/face OR the phone's PIN/pattern, so nobody is locked out of their chats.
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
