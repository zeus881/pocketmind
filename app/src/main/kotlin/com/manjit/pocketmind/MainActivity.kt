package com.manjit.pocketmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manjit.pocketmind.core.designsystem.theme.PocketMindTheme
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; Scaffold adds the right insets.
        enableEdgeToEdge()
        setContent {
            PocketMindTheme {
                PocketMindAppUi()
            }
        }
    }
}
