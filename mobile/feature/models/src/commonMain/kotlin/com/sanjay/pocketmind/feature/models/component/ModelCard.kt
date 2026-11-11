package com.sanjay.pocketmind.feature.models.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.designsystem.platform.rememberFileSizeFormatter
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.ModelKind
import com.sanjay.pocketmind.data.models.ModelState
import com.sanjay.pocketmind.data.models.ModelStatus
import com.sanjay.pocketmind.feature.models.resources.Res
import com.sanjay.pocketmind.feature.models.resources.feature_models_cancel
import com.sanjay.pocketmind.feature.models.resources.feature_models_delete
import com.sanjay.pocketmind.feature.models.resources.feature_models_delete_body
import com.sanjay.pocketmind.feature.models.resources.feature_models_delete_title
import com.sanjay.pocketmind.feature.models.resources.feature_models_download
import com.sanjay.pocketmind.feature.models.resources.feature_models_error_generic
import com.sanjay.pocketmind.feature.models.resources.feature_models_facts
import com.sanjay.pocketmind.feature.models.resources.feature_models_failed_checksum
import com.sanjay.pocketmind.feature.models.resources.feature_models_failed_network
import com.sanjay.pocketmind.feature.models.resources.feature_models_failed_storage
import com.sanjay.pocketmind.feature.models.resources.feature_models_in_use
import com.sanjay.pocketmind.feature.models.resources.feature_models_keep
import com.sanjay.pocketmind.feature.models.resources.feature_models_needs_pro
import com.sanjay.pocketmind.feature.models.resources.feature_models_needs_ram
import com.sanjay.pocketmind.feature.models.resources.feature_models_pause
import com.sanjay.pocketmind.feature.models.resources.feature_models_progress
import com.sanjay.pocketmind.feature.models.resources.feature_models_recommended
import com.sanjay.pocketmind.feature.models.resources.feature_models_resume
import com.sanjay.pocketmind.feature.models.resources.feature_models_retry
import com.sanjay.pocketmind.feature.models.resources.feature_models_use
import com.sanjay.pocketmind.feature.models.resources.feature_models_verify
import com.sanjay.pocketmind.feature.models.resources.feature_models_verifying
import com.sanjay.pocketmind.feature.models.resources.feature_models_waiting_network
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One model: what it is, whether it fits this phone, its download state and the actions. */
@Composable
fun ModelCard(
    model: ModelState,
    isRecommended: Boolean,
    actions: ModelActions,
    modifier: Modifier = Modifier,
    isVerifying: Boolean = false,
) {
    val fileSize = rememberFileSizeFormatter()
    val info = model.info
    val size = fileSize(info.sizeBytes)
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(PmSpacing.l), verticalArrangement = Arrangement.spacedBy(PmSpacing.s)) {
            Text(info.displayName, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PmSpacing.s)) {
                if (isRecommended) Badge(stringResource(Res.string.feature_models_recommended))
                if (model.isSelected) Badge(stringResource(Res.string.feature_models_in_use))
            }
            Text(info.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.feature_models_facts, size, info.minRamGb, info.license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusSection(model, actions, isVerifying, onAskDelete = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.feature_models_delete_title, info.displayName)) },
            text = { Text(stringResource(Res.string.feature_models_delete_body, size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete(info.id)
                }) { Text(stringResource(Res.string.feature_models_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.feature_models_keep)) }
            },
        )
    }
}

@Composable
private fun StatusSection(model: ModelState, actions: ModelActions, isVerifying: Boolean, onAskDelete: () -> Unit) {
    val id = model.info.id
    when (val status = model.status) {
        ModelStatus.NotDownloaded -> if (model.fitsInRam) {
            Button(onClick = { actions.onDownload(id) }) { Text(stringResource(Res.string.feature_models_download)) }
        } else {
            // Blocked on purpose: the model would crash or be unusably slow on this phone.
            Text(
                stringResource(Res.string.feature_models_needs_ram, model.info.minRamGb),
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ModelStatus.Queued -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(Res.string.feature_models_waiting_network), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                actions.onCancel(id)
            }) { Text(stringResource(Res.string.feature_models_cancel)) }
        }

        is ModelStatus.Downloading -> {
            Progress(status.downloaded, status.total)
            ButtonRow {
                OutlinedButton(onClick = {
                    actions.onPause(id)
                }) { Text(stringResource(Res.string.feature_models_pause)) }
                TextButton(onClick = {
                    actions.onCancel(id)
                }) { Text(stringResource(Res.string.feature_models_cancel)) }
            }
        }

        is ModelStatus.Paused -> {
            Progress(status.downloaded, status.total)
            ButtonRow {
                Button(onClick = { actions.onDownload(id) }) { Text(stringResource(Res.string.feature_models_resume)) }
                TextButton(onClick = {
                    actions.onCancel(id)
                }) { Text(stringResource(Res.string.feature_models_cancel)) }
            }
        }

        is ModelStatus.Failed -> {
            Text(stringResource(status.kind.messageRes()), color = MaterialTheme.colorScheme.error)
            ButtonRow {
                Button(onClick = { actions.onDownload(id) }) { Text(stringResource(Res.string.feature_models_retry)) }
                if (status.downloaded > 0) {
                    TextButton(onClick = {
                        actions.onCancel(id)
                    }) { Text(stringResource(Res.string.feature_models_cancel)) }
                }
            }
        }

        ModelStatus.Downloaded -> {
            if (isVerifying) {
                // Hashing gigabytes takes a few seconds: show that something is happening.
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(Res.string.feature_models_verifying), style = MaterialTheme.typography.bodySmall)
            }
            ButtonRow {
                if (model.info.kind == ModelKind.CHAT && !model.isSelected) {
                    Button(onClick = { actions.onSelect(id) }) { Text(stringResource(Res.string.feature_models_use)) }
                }
                OutlinedButton(onClick = { actions.onVerify(id) }, enabled = !isVerifying) {
                    Text(stringResource(Res.string.feature_models_verify))
                }
                OutlinedButton(onClick = onAskDelete, enabled = !isVerifying) {
                    Text(stringResource(Res.string.feature_models_delete))
                }
            }
        }
    }
}

@Composable
private fun Progress(downloaded: Long, total: Long) {
    val fileSize = rememberFileSizeFormatter()
    val fraction = if (total > 0) downloaded.toFloat() / total else 0f
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    Text(
        stringResource(
            Res.string.feature_models_progress,
            fileSize(downloaded),
            fileSize(total),
            (fraction * PERCENT).toInt(),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** A non-clickable label chip (SuggestionChip would announce itself as a button to TalkBack). */
@Composable
private fun Badge(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = PmSpacing.s, vertical = PmSpacing.xs),
        )
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PmSpacing.s)) { content() }
}

private const val PERCENT = 100

private fun ErrorKind.messageRes(): StringResource = when (this) {
    ErrorKind.NOT_ENOUGH_STORAGE -> Res.string.feature_models_failed_storage
    ErrorKind.CHECKSUM_MISMATCH -> Res.string.feature_models_failed_checksum
    ErrorKind.NETWORK -> Res.string.feature_models_failed_network
    ErrorKind.NEEDS_PRO -> Res.string.feature_models_needs_pro
    else -> Res.string.feature_models_error_generic
}
