package com.manjit.pocketmind.feature.models.component

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manjit.pocketmind.core.common.result.ErrorKind
import com.manjit.pocketmind.core.model.ModelKind
import com.manjit.pocketmind.data.models.ModelState
import com.manjit.pocketmind.data.models.ModelStatus
import com.manjit.pocketmind.feature.models.R

/** One model: what it is, whether it fits this phone, its download state and the actions. */
@Composable
fun ModelCard(model: ModelState, isRecommended: Boolean, actions: ModelActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = model.info
    val size = Formatter.formatShortFileSize(context, info.sizeBytes)
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(info.displayName, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRecommended) Badge(stringResource(R.string.feature_models_recommended))
                if (model.isSelected) Badge(stringResource(R.string.feature_models_in_use))
            }
            Text(info.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.feature_models_facts, size, info.minRamGb, info.license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusSection(model, actions, onAskDelete = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feature_models_delete_title, info.displayName)) },
            text = { Text(stringResource(R.string.feature_models_delete_body, size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete(info.id)
                }) { Text(stringResource(R.string.feature_models_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.feature_models_keep)) }
            },
        )
    }
}

@Composable
private fun StatusSection(model: ModelState, actions: ModelActions, onAskDelete: () -> Unit) {
    val id = model.info.id
    when (val status = model.status) {
        ModelStatus.NotDownloaded -> if (model.fitsInRam) {
            Button(onClick = { actions.onDownload(id) }) { Text(stringResource(R.string.feature_models_download)) }
        } else {
            // Blocked on purpose: the model would crash or be unusably slow on this phone.
            Text(
                stringResource(R.string.feature_models_needs_ram, model.info.minRamGb),
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ModelStatus.Queued -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.feature_models_waiting_network), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { actions.onCancel(id) }) { Text(stringResource(R.string.feature_models_cancel)) }
        }

        is ModelStatus.Downloading -> {
            Progress(status.downloaded, status.total)
            ButtonRow {
                OutlinedButton(onClick = {
                    actions.onPause(id)
                }) { Text(stringResource(R.string.feature_models_pause)) }
                TextButton(onClick = { actions.onCancel(id) }) { Text(stringResource(R.string.feature_models_cancel)) }
            }
        }

        is ModelStatus.Paused -> {
            Progress(status.downloaded, status.total)
            ButtonRow {
                Button(onClick = { actions.onDownload(id) }) { Text(stringResource(R.string.feature_models_resume)) }
                TextButton(onClick = { actions.onCancel(id) }) { Text(stringResource(R.string.feature_models_cancel)) }
            }
        }

        is ModelStatus.Failed -> {
            Text(stringResource(status.kind.messageRes()), color = MaterialTheme.colorScheme.error)
            ButtonRow {
                Button(onClick = { actions.onDownload(id) }) { Text(stringResource(R.string.feature_models_retry)) }
                if (status.downloaded > 0) {
                    TextButton(onClick = {
                        actions.onCancel(id)
                    }) { Text(stringResource(R.string.feature_models_cancel)) }
                }
            }
        }

        ModelStatus.Downloaded -> ButtonRow {
            if (model.info.kind == ModelKind.CHAT && !model.isSelected) {
                Button(onClick = { actions.onSelect(id) }) { Text(stringResource(R.string.feature_models_use)) }
            }
            OutlinedButton(onClick = onAskDelete) { Text(stringResource(R.string.feature_models_delete)) }
        }
    }
}

@Composable
private fun Progress(downloaded: Long, total: Long) {
    val context = LocalContext.current
    val fraction = if (total > 0) downloaded.toFloat() / total else 0f
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    Text(
        stringResource(
            R.string.feature_models_progress,
            Formatter.formatShortFileSize(context, downloaded),
            Formatter.formatShortFileSize(context, total),
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private const val PERCENT = 100

private fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.NOT_ENOUGH_STORAGE -> R.string.feature_models_failed_storage
    ErrorKind.CHECKSUM_MISMATCH -> R.string.feature_models_failed_checksum
    ErrorKind.NETWORK -> R.string.feature_models_failed_network
    else -> R.string.feature_models_error_generic
}
