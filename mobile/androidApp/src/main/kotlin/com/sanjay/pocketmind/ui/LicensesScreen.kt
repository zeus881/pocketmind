package com.sanjay.pocketmind.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.sanjay.pocketmind.R
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Open-source licences. The library list is generated at BUILD time by the AboutLibraries Gradle
 * plugin (res/raw/aboutlibraries.json, from each dependency's POM) — no network, always in sync
 * with the real dependencies. The models' licences are listed above it: they are downloaded
 * files, not Gradle dependencies, so the plugin can't see them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val resources = LocalResources.current
    // Reading and parsing the licence JSON is disk + CPU work: off the main thread (v2 phase 6,
    // found by the StrictMode audit). The screen shows a spinner for the few milliseconds it takes.
    val libs by produceState<Libs?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.licenses_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Centred at a readable width on tablets and foldables.
                .readableWidth(),
        ) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(PmSpacing.l),
            ) {
                Text(
                    stringResource(R.string.licenses_models),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(PmSpacing.l),
                )
            }
            val loaded = libs
            if (loaded == null) LoadingState() else LibrariesContainer(loaded, Modifier.fillMaxSize())
        }
    }
}
