package com.sanjay.pocketmind

import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

                is MainUiState.Ready -> PocketMindTheme(themeMode = current.settings.themeMode) {
                    // The start screen is decided ONCE; later settings changes must not reset navigation.
                    val start = remember { if (current.settings.onboardingDone) ChatRoute() else OnboardingRoute }
                    if (current.locked) {
                        LockScreen(onUnlock = ::authenticate)
                    } else {
                        PocketMindAppUi(startDestination = start)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app re-locks it — but not for a rotation (the activity is recreated, not left).
        if (!isChangingConfigurations) viewModel.lock()
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
