package com.sanjay.pocketmind.feature.models

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.component.ErrorState
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.platform.rememberFileSizeFormatter
import com.sanjay.pocketmind.core.designsystem.platform.rememberNotificationPermissionLauncher
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.feature.models.component.ModelActions
import com.sanjay.pocketmind.feature.models.component.ModelCard
import com.sanjay.pocketmind.feature.models.resources.Res
import com.sanjay.pocketmind.feature.models.resources.feature_models_back
import com.sanjay.pocketmind.feature.models.resources.feature_models_chat_section
import com.sanjay.pocketmind.feature.models.resources.feature_models_device_ram
import com.sanjay.pocketmind.feature.models.resources.feature_models_embedding_section
import com.sanjay.pocketmind.feature.models.resources.feature_models_error_generic
import com.sanjay.pocketmind.feature.models.resources.feature_models_error_storage
import com.sanjay.pocketmind.feature.models.resources.feature_models_mobile_data
import com.sanjay.pocketmind.feature.models.resources.feature_models_mobile_data_hint
import com.sanjay.pocketmind.feature.models.resources.feature_models_offline_note
import com.sanjay.pocketmind.feature.models.resources.feature_models_recommended_for_you
import com.sanjay.pocketmind.feature.models.resources.feature_models_title
import com.sanjay.pocketmind.feature.models.resources.feature_models_verify_damaged
import com.sanjay.pocketmind.feature.models.resources.feature_models_verify_ok
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful model manager screen. Asks for the notification permission (Android 13+) before
 * the first download so the progress notification can be shown; the download works either way.
 */
@Composable
fun ModelsScreen(onBack: (() -> Unit)?, viewModel: ModelsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val fileSize = rememberFileSizeFormatter()
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    var pendingDownload by rememberSaveable { mutableStateOf<String?>(null) }

    // The system permission dialog returns here; we download whatever the user answered.
    // Null when there is nothing to ask (Android before 13, iOS).
    val askNotifications = rememberNotificationPermissionLauncher {
        pendingDownload?.let(viewModel::download)
        pendingDownload = null
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ModelsEvent.Error -> snackbar.showSnackbar(
                    when (event.kind) {
                        ErrorKind.NOT_ENOUGH_STORAGE -> getString(
                            Res.string.feature_models_error_storage,
                            fileSize(event.neededBytes),
                        )

                        ErrorKind.CHECKSUM_MISMATCH -> getString(Res.string.feature_models_verify_damaged)

                        else -> getString(Res.string.feature_models_error_generic)
                    },
                )

                is ModelsEvent.Verified -> snackbar.showSnackbar(
                    getString(Res.string.feature_models_verify_ok, event.displayName),
                )
            }
        }
    }

    val actions = ModelActions(
        onDownload = { id ->
            if (askNotifications != null && !askedNotifications) {
                askedNotifications = true
                pendingDownload = id
                askNotifications()
            } else {
                viewModel.download(id)
            }
        },
        onPause = viewModel::pause,
        onCancel = viewModel::cancel,
        onDelete = viewModel::delete,
        onSelect = viewModel::select,
        onVerify = viewModel::verify,
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
                title = { Text(stringResource(Res.string.feature_models_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.feature_models_back))
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
            // Centred at a readable width on tablets and foldables.
            .readableWidth()
        when {
            state.isLoading -> LoadingState(modifier)

            // The catalog ships in the APK, so this is near-impossible; still, never a blank screen.
            state.loadFailed -> ErrorState(
                message = stringResource(Res.string.feature_models_error_generic),
                onRetry = {},
                modifier = modifier,
            )

            else -> LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(PmSpacing.l),
                verticalArrangement = Arrangement.spacedBy(PmSpacing.m),
            ) {
                item { DeviceCard(state) }
                item { MobileDataRow(state.allowMobileData, onAllowMobileData) }
                item { SectionHeader(stringResource(Res.string.feature_models_chat_section)) }
                items(state.chatModels, key = { it.info.id }) { model ->
                    ModelCard(
                        model,
                        isRecommended = model.info.id == state.recommendedId,
                        actions = actions,
                        isVerifying = model.info.id in state.verifyingIds,
                    )
                }
                if (state.embeddingModels.isNotEmpty()) {
                    item { SectionHeader(stringResource(Res.string.feature_models_embedding_section)) }
                    items(state.embeddingModels, key = { it.info.id }) { model ->
                        ModelCard(
                            model,
                            isRecommended = false,
                            actions = actions,
                            isVerifying = model.info.id in state.verifyingIds,
                        )
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
        Column(Modifier.padding(PmSpacing.l), verticalArrangement = Arrangement.spacedBy(PmSpacing.xs)) {
            Text(
                stringResource(Res.string.feature_models_device_ram, state.deviceRamGb),
                style = MaterialTheme.typography.titleMedium,
            )
            if (recommended != null) {
                Text(stringResource(Res.string.feature_models_recommended_for_you, recommended))
            }
            Text(
                stringResource(Res.string.feature_models_offline_note),
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
            .padding(vertical = PmSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.feature_models_mobile_data), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.feature_models_mobile_data_hint),
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
            .padding(top = PmSpacing.s)
            .semantics { heading() },
    )
}
