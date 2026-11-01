package com.manjit.pocketmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manjit.pocketmind.core.designsystem.theme.PocketMindTheme
import com.manjit.pocketmind.feature.chat.navigation.ChatRoute
import com.manjit.pocketmind.feature.onboarding.navigation.OnboardingRoute
import com.manjit.pocketmind.ui.PocketMindAppUi
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only Activity. With Compose + Navigation we use a "single-activity" app: screens are
 * composables swapped by the NavHost, not separate Activities.
 *
 * `@AndroidEntryPoint` lets Hilt inject into this Activity and into the ViewModels of the
 * screens it hosts.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
                    PocketMindAppUi(startDestination = start)
                }
            }
        }
    }
}
