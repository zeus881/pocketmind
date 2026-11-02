package com.sanjay.pocketmind.feature.models

import android.Manifest
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.ErrorState
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.feature.models.component.ModelActions
import com.sanjay.pocketmind.feature.models.component.ModelCard

/**
 * Stateful model manager screen. Asks for the notification permission (Android 13+) before
 * the first download so the progress notification can be shown; the download works either way.
 */
@Composable
fun ModelsScreen(onBack: (() -> Unit)?, viewModel: ModelsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    var pendingDownload by rememberSaveable { mutableStateOf<String?>(null) }

    // The system permission dialog returns here; we download whatever the user answered.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.let(viewModel::download)
        pendingDownload = null
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ModelsEvent.Error -> snackbar.showSnackbar(
                    when (event.kind) {
                        ErrorKind.NOT_ENOUGH_STORAGE -> resources.getString(
                            R.string.feature_models_error_storage,
                            Formatter.formatShortFileSize(context, event.neededBytes),
                        )

                        else -> resources.getString(R.string.feature_models_error_generic)
                    },
                )
            }
        }
    }

    val actions = ModelActions(
        onDownload = { id ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications) {
                askedNotifications = true
                pendingDownload = id
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.download(id)
            }
        },
        onPause = viewModel::pause,
        onCancel = viewModel::cancel,
        onDelete = viewModel::delete,
        onSelect = viewModel::select,
    )
    ModelsContent(state, actions, viewModel::setAllowMobileData, onBack, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsContent(
    state: ModelsUiState,
    actions: ModelActions,
    onAllowMobileData: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_models_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.feature_models_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingState(modifier)

            // The catalog ships in the APK, so this is near-impossible; still, never a blank screen.
            state.loadFailed -> ErrorState(
                message = stringResource(R.string.feature_models_error_generic),
                onRetry = {},
                modifier = modifier,
            )

            else -> LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DeviceCard(state) }
                item { MobileDataRow(state.allowMobileData, onAllowMobileData) }
                item { SectionHeader(stringResource(R.string.feature_models_chat_section)) }
                items(state.chatModels, key = { it.info.id }) { model ->
                    ModelCard(model, isRecommended = model.info.id == state.recommendedId, actions = actions)
                }
                if (state.embeddingModels.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.feature_models_embedding_section)) }
                    items(state.embeddingModels, key = { it.info.id }) { model ->
                        ModelCard(model, isRecommended = false, actions = actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(state: ModelsUiState) {
    val recommended = state.chatModels.firstOrNull { it.info.id == state.recommendedId }?.info?.displayName
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.feature_models_device_ram, state.deviceRamGb),
                style = MaterialTheme.typography.titleMedium,
            )
            if (recommended != null) {
                Text(stringResource(R.string.feature_models_recommended_for_you, recommended))
            }
            Text(
                stringResource(R.string.feature_models_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MobileDataRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is one toggle: a big touch target and one TalkBack announcement.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.feature_models_mobile_data), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.feature_models_mobile_data_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}
